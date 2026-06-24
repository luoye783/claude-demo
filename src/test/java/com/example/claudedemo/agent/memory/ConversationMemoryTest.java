package com.example.claudedemo.agent.memory;

import com.example.claudedemo.llm.ChatMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ConversationMemory} 单元测试.
 *
 * <p>测试 turn 级别 FIFO 裁剪、toChatMessages 转换、空白 sessionId 校验等.
 *
 * @since 0.0.1
 */
class ConversationMemoryTest {

    @Test
    void should_reject_blank_sessionId() {
        assertThrows(IllegalArgumentException.class, () -> new ConversationMemory(null));
        assertThrows(IllegalArgumentException.class, () -> new ConversationMemory(""));
        assertThrows(IllegalArgumentException.class, () -> new ConversationMemory("   "));
    }

    @Test
    void should_return_sessionId() {
        ConversationMemory memory = new ConversationMemory("session-1");
        assertEquals("session-1", memory.sessionId());
    }

    @Test
    void should_be_empty_initially() {
        ConversationMemory memory = new ConversationMemory("session-1");
        assertTrue(memory.isEmpty());
        assertEquals(0, memory.size());
    }

    @Test
    void should_add_and_retrieve_turns() {
        ConversationMemory memory = new ConversationMemory("session-1");
        memory.addTurn(new ConversationTurn("q1", "a1"));
        memory.addTurn(new ConversationTurn("q2", "a2"));

        assertEquals(2, memory.size());
        assertFalse(memory.isEmpty());

        List<ConversationTurn> turns = memory.turns();
        assertEquals("q1", turns.get(0).question());
        assertEquals("a1", turns.get(0).answer());
        assertEquals("q2", turns.get(1).question());
        assertEquals("a2", turns.get(1).answer());
    }

    @Test
    void should_ignore_null_turn() {
        ConversationMemory memory = new ConversationMemory("session-1");
        memory.addTurn(null);
        assertTrue(memory.isEmpty());
    }

    @Test
    void should_fifo_truncate_when_exceeding_max_turns() {
        ConversationMemory memory = new ConversationMemory("session-1");
        // 添加 MAX_TURNS + 5 个 turn
        for (int i = 1; i <= ConversationMemory.MAX_TURNS + 5; i++) {
            memory.addTurn(new ConversationTurn("q" + i, "a" + i));
        }

        // 最多保留 MAX_TURNS 个
        assertEquals(ConversationMemory.MAX_TURNS, memory.size());

        // 最旧的 5 个被裁掉
        List<ConversationTurn> turns = memory.turns();
        assertEquals("q6", turns.get(0).question());  // 第 6 个变成了最旧的
        assertEquals("a" + (ConversationMemory.MAX_TURNS + 5), turns.get(turns.size() - 1).answer());
    }

    @Test
    void should_produce_chat_messages_from_turns() {
        ConversationMemory memory = new ConversationMemory("session-1");
        memory.addTurn(new ConversationTurn("Hello", "Hi there!"));
        memory.addTurn(new ConversationTurn("How are you?", "I'm fine."));

        List<ChatMessage> msgs = memory.toChatMessages();
        // 每个 turn → 2 条消息: user + assistant
        assertEquals(4, msgs.size());
        assertEquals("user", msgs.get(0).role());
        assertEquals("Hello", msgs.get(0).content());
        assertEquals("assistant", msgs.get(1).role());
        assertEquals("Hi there!", msgs.get(1).content());
        assertEquals("user", msgs.get(2).role());
        assertEquals("How are you?", msgs.get(2).content());
        assertEquals("assistant", msgs.get(3).role());
        assertEquals("I'm fine.", msgs.get(3).content());
    }

    @Test
    void should_return_empty_chat_messages_when_no_turns() {
        ConversationMemory memory = new ConversationMemory("session-1");
        assertTrue(memory.toChatMessages().isEmpty());
    }

    @Test
    void should_return_immutable_turns_copy() {
        ConversationMemory memory = new ConversationMemory("session-1");
        memory.addTurn(new ConversationTurn("q", "a"));

        List<ConversationTurn> turns = memory.turns();
        assertThrows(UnsupportedOperationException.class, () -> turns.add(new ConversationTurn("x", "y")));
    }

    @Test
    void should_return_immutable_chat_messages_copy() {
        ConversationMemory memory = new ConversationMemory("session-1");
        memory.addTurn(new ConversationTurn("q", "a"));

        List<ChatMessage> msgs = memory.toChatMessages();
        assertThrows(UnsupportedOperationException.class, () -> msgs.add(new ChatMessage("user", "spam")));
    }

    @Test
    void should_clear_all_turns() {
        ConversationMemory memory = new ConversationMemory("session-1");
        memory.addTurn(new ConversationTurn("q1", "a1"));
        memory.addTurn(new ConversationTurn("q2", "a2"));

        memory.clear();
        assertTrue(memory.isEmpty());
        assertEquals(0, memory.size());
    }

    // ==================== 摘要压缩(V2 第四阶段) ====================

    @Test
    void should_have_empty_summary_initially() {
        ConversationMemory memory = new ConversationMemory("session-1");
        assertTrue(memory.summary().isEmpty());
        assertFalse(memory.hasSummary());
    }

    @Test
    void should_set_and_get_summary() {
        ConversationMemory memory = new ConversationMemory("session-1");
        SummaryMemory s = new SummaryMemory("旧摘要", List.of("f1"), 1L);
        memory.setSummary(s);
        assertTrue(memory.hasSummary());
        assertEquals("旧摘要", memory.summary().summary());
        assertEquals(1L, memory.summary().version());
    }

    @Test
    void should_treat_null_setSummary_as_empty() {
        ConversationMemory memory = new ConversationMemory("session-1");
        memory.setSummary(new SummaryMemory("x", List.of("y"), 1L));
        memory.setSummary(null);
        assertTrue(memory.summary().isEmpty());
        assertFalse(memory.hasSummary());
    }

    @Test
    void should_collect_turns_to_evict() {
        ConversationMemory memory = new ConversationMemory("session-1");
        for (int i = 1; i <= 5; i++) {
            memory.addTurn(new ConversationTurn("q" + i, "a" + i));
        }
        // size=5, keepRecent=2 → 淘汰前 3 条
        List<ConversationTurn> evicted = memory.turnsToEvict(2);
        assertEquals(3, evicted.size());
        assertEquals("q1", evicted.get(0).question());
        assertEquals("a3", evicted.get(2).answer());
        // 不可变副本
        assertThrows(UnsupportedOperationException.class, () -> evicted.add(new ConversationTurn("x", "y")));
        // 原 memory 不受影响
        assertEquals(5, memory.size());
    }

    @Test
    void should_return_empty_evict_when_size_le_keepRecent() {
        ConversationMemory memory = new ConversationMemory("session-1");
        memory.addTurn(new ConversationTurn("q1", "a1"));
        memory.addTurn(new ConversationTurn("q2", "a2"));
        assertTrue(memory.turnsToEvict(5).isEmpty());
        assertTrue(memory.turnsToEvict(2).isEmpty());
    }

    @Test
    void should_reject_negative_keepRecent() {
        ConversationMemory memory = new ConversationMemory("session-1");
        assertThrows(IllegalArgumentException.class, () -> memory.turnsToEvict(-1));
    }

    @Test
    void should_drop_oldest_n_turns() {
        ConversationMemory memory = new ConversationMemory("session-1");
        for (int i = 1; i <= 5; i++) {
            memory.addTurn(new ConversationTurn("q" + i, "a" + i));
        }
        memory.dropOldest(2);
        assertEquals(3, memory.size());
        assertEquals("q3", memory.turns().get(0).question());
        assertEquals("q5", memory.turns().get(2).question());
    }

    @Test
    void should_drop_oldest_noop_when_count_le_zero() {
        ConversationMemory memory = new ConversationMemory("session-1");
        memory.addTurn(new ConversationTurn("q", "a"));
        memory.dropOldest(0);
        memory.dropOldest(-5);
        assertEquals(1, memory.size());
    }

    @Test
    void should_drop_oldest_more_than_size_empties_turns() {
        ConversationMemory memory = new ConversationMemory("session-1");
        memory.addTurn(new ConversationTurn("q1", "a1"));
        memory.addTurn(new ConversationTurn("q2", "a2"));
        memory.dropOldest(10);
        assertTrue(memory.isEmpty());
    }
}
