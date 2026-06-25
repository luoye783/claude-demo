package com.example.claudedemo.agent.session;

import com.example.claudedemo.agent.memory.ConversationMemory;
import com.example.claudedemo.agent.trace.AgentTrace;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AgentSession} 单元测试.
 *
 * @since 0.0.1
 */
class AgentSessionTest {

    @Test
    void should_build_with_required_fields() {
        AgentTrace trace = new AgentTrace();
        ConversationMemory memory = new ConversationMemory("sid");

        AgentSession session = new AgentSession("sid", memory, trace);

        assertEquals("sid", session.sessionId());
        assertEquals(memory, session.memory());
        assertEquals(trace, session.trace());
        assertNotNull(session.tokenUsage());
        assertEquals(0L, session.tokenUsage().totalTokens());
        assertNotNull(session.metadataView());
        assertTrue(session.metadataView().isEmpty());
        assertTrue(session.createdAtMs() > 0L);
    }

    @Test
    void should_have_null_memory_field_for_no_memory_mode() {
        AgentSession session = new AgentSession(null, null, new AgentTrace());

        assertNull(session.sessionId());
        assertNull(session.memory());
        assertFalse(session.hasMemory());
        // trace 与 tokenUsage 仍非空
        assertNotNull(session.trace());
        assertNotNull(session.tokenUsage());
    }

    @Test
    void should_report_hasMemory() {
        AgentSession withMemory = new AgentSession("s", new ConversationMemory("s"), new AgentTrace());
        AgentSession withoutMemory = new AgentSession("s", null, new AgentTrace());

        assertTrue(withMemory.hasMemory());
        assertFalse(withoutMemory.hasMemory());
    }

    @Test
    void should_put_and_get_metadata() {
        AgentSession session = new AgentSession("s", null, new AgentTrace());
        session.put("user", "alice");
        session.put("count", 42);

        assertEquals("alice", session.get("user"));
        assertEquals(42, session.get("count"));
    }

    @Test
    void should_overwrite_existing_key() {
        AgentSession session = new AgentSession("s", null, new AgentTrace());
        session.put("k", "v1");
        session.put("k", "v2");
        assertEquals("v2", session.get("k"));
    }

    @Test
    void should_allow_null_value_in_metadata() {
        AgentSession session = new AgentSession("s", null, new AgentTrace());
        session.put("k", null);
        assertTrue(session.contains("k"));
        assertNull(session.get("k"));
    }

    @Test
    void should_reject_null_key() {
        AgentSession session = new AgentSession("s", null, new AgentTrace());
        assertThrows(IllegalArgumentException.class, () -> session.put(null, "v"));
    }

    @Test
    void should_return_null_for_missing_key() {
        AgentSession session = new AgentSession("s", null, new AgentTrace());
        assertNull(session.get("missing"));
        assertFalse(session.contains("missing"));
    }

    @Test
    void should_return_immutable_metadata_view() {
        AgentSession session = new AgentSession("s", null, new AgentTrace());
        session.put("k", "v");

        Map<String, Object> view = session.metadataView();
        assertEquals("v", view.get("k"));
        // 不可变: 试图 mutate 抛 UOE
        assertThrows(UnsupportedOperationException.class, () -> view.put("x", "y"));
    }

    @Test
    void should_return_defensive_copy_in_metadataView() {
        AgentSession session = new AgentSession("s", null, new AgentTrace());
        session.put("k", "v1");

        Map<String, Object> view = session.metadataView();
        // 修改 session 不影响 view
        session.put("k", "v2");
        assertEquals("v1", view.get("k"));
    }

    @Test
    void should_set_createdAtMs_from_constructor() {
        AgentSession session = new AgentSession("s", null, new AgentTrace(),
                new TokenUsage(), new HashMap<>(), 1700000000000L);
        assertEquals(1700000000000L, session.createdAtMs());
    }

    @Test
    void should_set_createdAtMs_via_builder() {
        AgentSession session = AgentSession.builder()
                .sessionId("s")
                .createdAtMs(123L)
                .build();
        assertEquals(123L, session.createdAtMs());
    }

    @Test
    void should_auto_set_createdAtMs_via_builder_when_not_specified() {
        long before = System.currentTimeMillis();
        AgentSession session = AgentSession.builder().sessionId("s").build();
        long after = System.currentTimeMillis();

        assertTrue(session.createdAtMs() >= before);
        assertTrue(session.createdAtMs() <= after);
    }

    @Test
    void should_have_independent_tokenUsage_per_session() {
        TokenUsage a = new TokenUsage();
        a.addPrompt(100);
        TokenUsage b = new TokenUsage();
        b.addPrompt(50);

        AgentSession sa = new AgentSession("a", null, new AgentTrace(), a, null, 0L);
        AgentSession sb = new AgentSession("b", null, new AgentTrace(), b, null, 0L);

        assertEquals(100L, sa.tokenUsage().promptTokens());
        assertEquals(50L, sb.tokenUsage().promptTokens());
        assertNotSame(sa.tokenUsage(), sb.tokenUsage());
    }
}
