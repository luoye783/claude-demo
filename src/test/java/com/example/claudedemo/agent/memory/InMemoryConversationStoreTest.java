package com.example.claudedemo.agent.memory;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link InMemoryConversationStore} 单元测试.
 *
 * @since 0.0.1
 */
class InMemoryConversationStoreTest {

    private final InMemoryConversationStore store = new InMemoryConversationStore();

    @Test
    void should_create_new_memory_when_getOrCreate_new_session() {
        ConversationMemory memory = store.getOrCreate("session-new");
        assertNotNull(memory);
        assertEquals("session-new", memory.sessionId());
        assertTrue(memory.isEmpty());
        assertEquals(1, store.size());
    }

    @Test
    void should_return_same_memory_for_same_session() {
        ConversationMemory m1 = store.getOrCreate("session-1");
        ConversationMemory m2 = store.getOrCreate("session-1");
        assertEquals(m1, m2);  // 同一引用
        assertEquals(1, store.size());
    }

    @Test
    void should_isolate_different_sessions() {
        ConversationMemory m1 = store.getOrCreate("session-a");
        ConversationMemory m2 = store.getOrCreate("session-b");
        m1.addTurn(new ConversationTurn("q1", "a1"));

        assertEquals(1, m1.size());
        assertTrue(m2.isEmpty());
        assertEquals(2, store.size());
    }

    @Test
    void should_return_empty_optional_for_unknown_session() {
        Optional<ConversationMemory> found = store.find("nonexistent");
        assertTrue(found.isEmpty());
    }

    @Test
    void should_return_memory_via_find() {
        store.getOrCreate("session-1");
        Optional<ConversationMemory> found = store.find("session-1");
        assertTrue(found.isPresent());
        assertEquals("session-1", found.get().sessionId());
    }

    @Test
    void should_report_correct_size() {
        assertEquals(0, store.size());
        store.getOrCreate("s1");
        assertEquals(1, store.size());
        store.getOrCreate("s2");
        assertEquals(2, store.size());
        // 重复 getOrCreate 不增加 size
        store.getOrCreate("s1");
        assertEquals(2, store.size());
    }

    @Test
    void should_throw_when_getOrCreate_with_null_sessionId() {
        assertThrows(IllegalArgumentException.class, () -> store.getOrCreate(null));
    }

    @Test
    void should_throw_when_getOrCreate_with_blank_sessionId() {
        assertThrows(IllegalArgumentException.class, () -> store.getOrCreate(""));
        assertThrows(IllegalArgumentException.class, () -> store.getOrCreate("  "));
    }
}
