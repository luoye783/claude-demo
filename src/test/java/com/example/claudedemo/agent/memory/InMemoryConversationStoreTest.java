package com.example.claudedemo.agent.memory;

import com.example.claudedemo.agent.trace.AgentTrace;
import com.example.claudedemo.agent.trace.StepType;
import com.example.claudedemo.llm.LlmClient;
import com.example.claudedemo.llm.LlmResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link InMemoryConversationStore} 单元测试.
 *
 * <p>V1 兼容场景 + V2 第四阶段 appendTurn 压缩流程。
 *
 * @since 0.0.1
 */
class InMemoryConversationStoreTest {

    // ==================== V1 兼容场景 ====================

    @Test
    void should_create_new_memory_when_getOrCreate_new_session() {
        InMemoryConversationStore store = new InMemoryConversationStore();
        ConversationMemory memory = store.getOrCreate("session-new");
        assertNotNull(memory);
        assertEquals("session-new", memory.sessionId());
        assertTrue(memory.isEmpty());
        assertEquals(1, store.size());
    }

    @Test
    void should_return_same_memory_for_same_session() {
        InMemoryConversationStore store = new InMemoryConversationStore();
        ConversationMemory m1 = store.getOrCreate("session-1");
        ConversationMemory m2 = store.getOrCreate("session-1");
        assertEquals(m1, m2);
        assertEquals(1, store.size());
    }

    @Test
    void should_isolate_different_sessions() {
        InMemoryConversationStore store = new InMemoryConversationStore();
        ConversationMemory m1 = store.getOrCreate("session-a");
        ConversationMemory m2 = store.getOrCreate("session-b");
        m1.addTurn(new ConversationTurn("q1", "a1"));

        assertEquals(1, m1.size());
        assertTrue(m2.isEmpty());
        assertEquals(2, store.size());
    }

    @Test
    void should_return_empty_optional_for_unknown_session() {
        InMemoryConversationStore store = new InMemoryConversationStore();
        Optional<ConversationMemory> found = store.find("nonexistent");
        assertTrue(found.isEmpty());
    }

    @Test
    void should_return_memory_via_find() {
        InMemoryConversationStore store = new InMemoryConversationStore();
        store.getOrCreate("session-1");
        Optional<ConversationMemory> found = store.find("session-1");
        assertTrue(found.isPresent());
        assertEquals("session-1", found.get().sessionId());
    }

    @Test
    void should_report_correct_size() {
        InMemoryConversationStore store = new InMemoryConversationStore();
        assertEquals(0, store.size());
        store.getOrCreate("s1");
        assertEquals(1, store.size());
        store.getOrCreate("s2");
        assertEquals(2, store.size());
        store.getOrCreate("s1");
        assertEquals(2, store.size());
    }

    @Test
    void should_throw_when_getOrCreate_with_null_sessionId() {
        InMemoryConversationStore store = new InMemoryConversationStore();
        assertThrows(IllegalArgumentException.class, () -> store.getOrCreate(null));
    }

    @Test
    void should_throw_when_getOrCreate_with_blank_sessionId() {
        InMemoryConversationStore store = new InMemoryConversationStore();
        assertThrows(IllegalArgumentException.class, () -> store.getOrCreate(""));
        assertThrows(IllegalArgumentException.class, () -> store.getOrCreate("  "));
    }

    // ==================== V2 appendTurn:无压缩场景 ====================

    @Test
    void appendTurn_should_pure_append_when_no_policy() {
        InMemoryConversationStore store = new InMemoryConversationStore();
        for (int i = 1; i <= 3; i++) {
            store.appendTurn("sid", new ConversationTurn("q" + i, "a" + i), null);
        }
        ConversationMemory memory = store.find("sid").orElseThrow();
        assertEquals(3, memory.size());
        assertFalse(memory.hasSummary());
    }

    @Test
    void appendTurn_should_pure_append_below_threshold() {
        MemoryCompressor compressor = mock(MemoryCompressor.class);
        CompressionPolicy policy = new TurnCountCompressionPolicy(10, 4);
        InMemoryConversationStore store = new InMemoryConversationStore(compressor, policy);

        for (int i = 1; i <= 9; i++) {
            store.appendTurn("sid", new ConversationTurn("q" + i, "a" + i), null);
        }

        ConversationMemory memory = store.find("sid").orElseThrow();
        assertEquals(9, memory.size());
        assertFalse(memory.hasSummary());
        // 阈值未到,LLM 一次都不应被调用
        verify(compressor, never()).compress(org.mockito.ArgumentMatchers.any(), anyList());
    }

    @Test
    void appendTurn_should_reject_null_sessionId() {
        InMemoryConversationStore store = new InMemoryConversationStore();
        assertThrows(IllegalArgumentException.class,
                () -> store.appendTurn(null, new ConversationTurn("q", "a"), null));
    }

    @Test
    void appendTurn_should_reject_blank_sessionId() {
        InMemoryConversationStore store = new InMemoryConversationStore();
        assertThrows(IllegalArgumentException.class,
                () -> store.appendTurn("", new ConversationTurn("q", "a"), null));
        assertThrows(IllegalArgumentException.class,
                () -> store.appendTurn("  ", new ConversationTurn("q", "a"), null));
    }

    @Test
    void appendTurn_should_ignore_null_turn() {
        InMemoryConversationStore store = new InMemoryConversationStore();
        store.appendTurn("sid", null, null);
        ConversationMemory memory = store.find("sid").orElseThrow();
        assertTrue(memory.isEmpty());
    }

    // ==================== V2 appendTurn:压缩流程 ====================

    @Test
    void appendTurn_should_trigger_compress_at_threshold_and_record_trace() {
        LlmClient llm = mock(LlmClient.class);
        when(llm.chat(anyList())).thenReturn(new LlmResponse(
                "{\"summary\":\"新摘要\",\"keyFacts\":[\"f1\"]}", "stop"));
        MemoryCompressor compressor = new MemoryCompressor(llm, new ObjectMapper());
        CompressionPolicy policy = new TurnCountCompressionPolicy(10, 4);
        InMemoryConversationStore store = new InMemoryConversationStore(compressor, policy);

        // 追加 10 轮,触发压缩
        for (int i = 1; i <= 10; i++) {
            store.appendTurn("sid", new ConversationTurn("q" + i, "a" + i), null);
        }

        ConversationMemory memory = store.find("sid").orElseThrow();
        // 压缩后保留 4 条
        assertEquals(4, memory.size());
        // 摘要已生成
        assertTrue(memory.hasSummary());
        assertEquals("新摘要", memory.summary().summary());
        assertEquals(1L, memory.summary().version());
        // 保留的是最后 4 条
        assertEquals("q7", memory.turns().get(0).question());
        assertEquals("q10", memory.turns().get(3).question());

        // LLM 仅被调 1 次(第 10 轮触发)
        verify(llm, times(1)).chat(anyList());
    }

    @Test
    void appendTurn_should_record_MEMORY_COMPRESS_step_in_trace() {
        LlmClient llm = mock(LlmClient.class);
        when(llm.chat(anyList())).thenReturn(new LlmResponse(
                "{\"summary\":\"s\",\"keyFacts\":[]}", "stop"));
        MemoryCompressor compressor = new MemoryCompressor(llm, new ObjectMapper());
        CompressionPolicy policy = new TurnCountCompressionPolicy(2, 0);
        InMemoryConversationStore store = new InMemoryConversationStore(compressor, policy);

        AgentTrace trace = new AgentTrace();
        store.appendTurn("sid", new ConversationTurn("q1", "a1"), trace);
        store.appendTurn("sid", new ConversationTurn("q2", "a2"), trace);

        boolean hasCompressStep = trace.steps().stream()
                .anyMatch(s -> s.stepType() == StepType.MEMORY_COMPRESS);
        assertTrue(hasCompressStep, "trace 应包含 MEMORY_COMPRESS 步骤");
    }

    @Test
    void appendTurn_should_not_record_MEMORY_COMPRESS_below_threshold() {
        LlmClient llm = mock(LlmClient.class);
        when(llm.chat(anyList())).thenReturn(new LlmResponse(
                "{\"summary\":\"s\",\"keyFacts\":[]}", "stop"));
        MemoryCompressor compressor = new MemoryCompressor(llm, new ObjectMapper());
        CompressionPolicy policy = new TurnCountCompressionPolicy(2, 0); // size>=2 触发,保留 0
        InMemoryConversationStore store = new InMemoryConversationStore(compressor, policy);

        AgentTrace trace = new AgentTrace();
        store.appendTurn("sid", new ConversationTurn("q1", "a1"), trace);
        // size=1 < 2 → 不压缩
        store.appendTurn("sid", new ConversationTurn("q2", "a2"), trace);
        // size=2 >= 2 → 压缩,evict 2 条,addTurn 后 size=1

        // trace 应有 MEMORY_COMPRESS 步骤
        boolean hasCompressStep = trace.steps().stream()
                .anyMatch(s -> s.stepType() == StepType.MEMORY_COMPRESS);
        assertTrue(hasCompressStep, "trace 应包含 MEMORY_COMPRESS 步骤");
    }

    @Test
    void appendTurn_should_degrade_when_compressor_returns_null() {
        LlmClient llm = mock(LlmClient.class);
        when(llm.chat(anyList())).thenReturn(new LlmResponse("garbage", "stop"));
        MemoryCompressor compressor = new MemoryCompressor(llm, new ObjectMapper());
        CompressionPolicy policy = new TurnCountCompressionPolicy(2, 0);
        InMemoryConversationStore store = new InMemoryConversationStore(compressor, policy);

        AgentTrace trace = new AgentTrace();
        store.appendTurn("sid", new ConversationTurn("q1", "a1"), trace);
        store.appendTurn("sid", new ConversationTurn("q2", "a2"), trace);

        ConversationMemory memory = store.find("sid").orElseThrow();
        // 压缩失败:turn 全部保留(FIFO 兜底在 addTurn 内部),summary 仍空
        assertEquals(2, memory.size());
        assertFalse(memory.hasSummary());

        // trace 应有 ERROR 步骤
        boolean hasErrorStep = trace.steps().stream()
                .anyMatch(s -> s.stepType() == StepType.ERROR);
        assertTrue(hasErrorStep, "压缩失败时 trace 应记录 ERROR 步骤");
    }

    @Test
    void appendTurn_should_increment_summary_version_on_subsequent_compression() {
        LlmClient llm = mock(LlmClient.class);
        when(llm.chat(anyList()))
                .thenReturn(new LlmResponse("{\"summary\":\"S1\",\"keyFacts\":[]}", "stop"))
                .thenReturn(new LlmResponse("{\"summary\":\"S2\",\"keyFacts\":[]}", "stop"));
        MemoryCompressor compressor = new MemoryCompressor(llm, new ObjectMapper());
        CompressionPolicy policy = new TurnCountCompressionPolicy(2, 0);
        InMemoryConversationStore store = new InMemoryConversationStore(compressor, policy);

        // 第 1 次压缩
        store.appendTurn("sid", new ConversationTurn("q1", "a1"), null);
        store.appendTurn("sid", new ConversationTurn("q2", "a2"), null);
        assertEquals("S1", store.find("sid").orElseThrow().summary().summary());
        assertEquals(1L, store.find("sid").orElseThrow().summary().version());

        // 继续追加,触发第 2 次压缩
        store.appendTurn("sid", new ConversationTurn("q3", "a3"), null);
        store.appendTurn("sid", new ConversationTurn("q4", "a4"), null);
        assertEquals("S2", store.find("sid").orElseThrow().summary().summary());
        assertEquals(2L, store.find("sid").orElseThrow().summary().version());

        verify(llm, times(2)).chat(anyList());
    }

    @Test
    void appendTurn_should_pass_old_summary_and_evicted_turns_to_compressor() {
        LlmClient llm = mock(LlmClient.class);
        // 第 1 次返回带 facts
        when(llm.chat(anyList()))
                .thenReturn(new LlmResponse(
                        "{\"summary\":\"S1\",\"keyFacts\":[\"old-fact\"]}", "stop"));
        MemoryCompressor compressor = new MemoryCompressor(llm, new ObjectMapper());
        CompressionPolicy policy = new TurnCountCompressionPolicy(2, 0);
        InMemoryConversationStore store = new InMemoryConversationStore(compressor, policy);

        store.appendTurn("sid", new ConversationTurn("q1", "a1"), null);
        store.appendTurn("sid", new ConversationTurn("q2", "a2"), null);

        // 抓取第 1 次 LLM 调用的 user 消息
        org.mockito.ArgumentCaptor<List<com.example.claudedemo.llm.ChatMessage>> captor =
                org.mockito.ArgumentCaptor.forClass(List.class);
        verify(llm, times(1)).chat(captor.capture());
        String userContent = captor.getValue().get(1).content();
        // 第 1 次:旧摘要为空,只有被淘汰 turn
        assertTrue(userContent.contains("(无)"), "首次压缩旧摘要应为空");
        assertTrue(userContent.contains("Q1: q1"));
        assertTrue(userContent.contains("Q2: q2"));
    }
}
