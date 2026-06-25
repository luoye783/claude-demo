package com.example.claudedemo.agent.mcp;

import com.example.claudedemo.agent.ToolCallingExhaustedException;
import com.example.claudedemo.agent.ToolCallingResult;
import com.example.claudedemo.agent.memory.ConversationMemory;
import com.example.claudedemo.agent.memory.InMemoryConversationStore;
import com.example.claudedemo.agent.memory.SummaryMemory;
import com.example.claudedemo.agent.session.AgentSession;
import com.example.claudedemo.agent.trace.AgentTrace;
import com.example.claudedemo.agent.trace.StepType;
import com.example.claudedemo.llm.ChatMessage;
import com.example.claudedemo.llm.LlmClient;
import com.example.claudedemo.llm.LlmResponse;
import com.example.claudedemo.llm.ToolCall;
import com.example.claudedemo.llm.ToolDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link Nl2SqlMcpAgent} V2 第五阶段 AgentSession 集成测试.
 *
 * <p>验证:公开 API 兼容、agent 内部围绕 AgentSession 工作、session 字段被正确填充。
 *
 * @since 0.0.1
 */
class Nl2SqlMcpAgentSessionTest {

    private static final List<ToolDefinition> FAKE_TOOL_DEFS = List.of(
            new ToolDefinition("get_schema",
                    "获取数据库所有表的结构",
                    Map.of("type", "object", "properties", Map.of(), "required", List.of())),
            new ToolDefinition("execute_sql",
                    "执行只读 SELECT SQL",
                    Map.of("type", "object", "properties", Map.of(
                            "sql", Map.of("type", "string")
                    ), "required", List.of("sql")))
    );

    private LlmClient llmClient;
    private McpToolClient mcpToolClient;
    private InMemoryConversationStore store;
    private Nl2SqlMcpAgent agent;

    @BeforeEach
    void setUp() {
        llmClient = mock(LlmClient.class);
        mcpToolClient = mock(McpToolClient.class);
        lenient().when(mcpToolClient.listTools()).thenReturn(FAKE_TOOL_DEFS);
        store = new InMemoryConversationStore();
        agent = new Nl2SqlMcpAgent(llmClient, mcpToolClient, store);
    }

    // ==================== AgentSession 字段填充 ====================

    @Test
    void should_populate_session_fields_on_success() {
        when(llmClient.chatWithTools(anyList(), anyList()))
                .thenReturn(new LlmResponse("答", "stop", null));

        ToolCallingResult result = agent.answer("sid-1", "问题");

        // result.trace() 与 session.trace() 是同一引用
        AgentSession session = AgentSession.builder()
                .sessionId("sid-1")
                .memory(store.find("sid-1").orElseThrow())
                .trace(result.trace())
                .build();
        assertNotNull(session);
        assertNotNull(session.trace());
        assertNotNull(session.memory());
        assertTrue(session.hasMemory());
        assertEquals("sid-1", session.sessionId());
    }

    @Test
    void should_have_no_memory_in_no_session_mode() {
        when(llmClient.chatWithTools(anyList(), anyList()))
                .thenReturn(new LlmResponse("答", "stop", null));

        // 无 sessionId 模式
        ToolCallingResult result = agent.answer("Q");

        AgentSession session = AgentSession.builder()
                .sessionId(null)
                .memory(null)
                .trace(result.trace())
                .build();
        assertFalse(session.hasMemory());
        assertNull(session.sessionId());
        // trace 仍非空
        assertNotNull(session.trace());
    }

    @Test
    void should_keep_trace_compatible_with_session_path() {
        when(llmClient.chatWithTools(anyList(), anyList()))
                .thenReturn(new LlmResponse("最终答案", "stop", null));

        ToolCallingResult result = agent.answer("sid-trace", "Q");

        // 与老测试相同的 trace 顺序断言
        List<StepType> expectedOrder = List.of(
                StepType.USER_QUESTION,
                StepType.LLM_REQUEST, StepType.LLM_RESPONSE,
                StepType.FINAL_ANSWER
        );
        List<StepType> actualOrder = result.trace().steps().stream()
                .map(s -> s.stepType()).toList();
        assertEquals(expectedOrder, actualOrder);
    }

    // ==================== Summary 注入 ====================

    @Test
    void should_attach_summary_via_session_path() {
        when(llmClient.chatWithTools(anyList(), anyList()))
                .thenReturn(new LlmResponse("答", "stop", null));

        // 预置 summary
        ConversationMemory memory = store.getOrCreate("sid-sum");
        memory.setSummary(new SummaryMemory("S1", List.of("f1"), 1L));

        // 抓取 messages 快照(doAnswer 拷贝,避免被 agent 后续 mutate)
        final List<List<ChatMessage>> capturedCalls = new ArrayList<>();
        org.mockito.Mockito.doAnswer(inv -> {
            capturedCalls.add(new ArrayList<>(inv.getArgument(0)));
            return new LlmResponse("答", "stop", null);
        }).when(llmClient).chatWithTools(anyList(), anyList());

        agent.answer("sid-sum", "Q");

        List<ChatMessage> msgs = capturedCalls.get(0);
        long systemCount = msgs.stream().filter(m -> m.role().equals("system")).count();
        assertEquals(2, systemCount, "session 含 summary 时应有 2 条 system(主 prompt + 摘要)");
        assertTrue(msgs.stream().anyMatch(m ->
                m.role().equals("system") && m.content().contains("S1")));
    }

    @Test
    void should_not_inject_summary_when_session_has_no_memory() {
        when(llmClient.chatWithTools(anyList(), anyList()))
                .thenReturn(new LlmResponse("答", "stop", null));

        final List<List<ChatMessage>> capturedCalls = new ArrayList<>();
        org.mockito.Mockito.doAnswer(inv -> {
            capturedCalls.add(new ArrayList<>(inv.getArgument(0)));
            return new LlmResponse("答", "stop", null);
        }).when(llmClient).chatWithTools(anyList(), anyList());

        agent.answer("Q"); // 无 sessionId

        List<ChatMessage> msgs = capturedCalls.get(0);
        long systemCount = msgs.stream().filter(m -> m.role().equals("system")).count();
        assertEquals(1, systemCount, "无 sessionId 模式下不应有摘要 system 消息");
    }

    // ==================== 写回 turn ====================

    @Test
    void should_write_turn_on_success_through_session() {
        when(llmClient.chatWithTools(anyList(), anyList()))
                .thenReturn(new LlmResponse("答", "stop", null));

        agent.answer("sid-w", "Q1");
        agent.answer("sid-w", "Q2");

        ConversationMemory memory = store.find("sid-w").orElseThrow();
        assertEquals(2, memory.size());
        assertEquals("Q1", memory.turns().get(0).question());
        assertEquals("Q2", memory.turns().get(1).question());
    }

    @Test
    void should_not_write_turn_on_failure_through_session() {
        // 构造 exhausted:持续 tool_calls 不给最终答案
        when(mcpToolClient.callTool(eq("get_schema"), anyString()))
                .thenReturn("schema");
        when(llmClient.chatWithTools(anyList(), anyList()))
                .thenReturn(new LlmResponse(null, "tool_calls", List.of(
                        new ToolCall("c1", "function", new ToolCall.Function("get_schema", "{}")))));

        assertThrows(ToolCallingExhaustedException.class,
                () -> agent.answer("sid-fail", "Q"));

        // memory 应为空(异常路径不写 turn)
        ConversationMemory memory = store.find("sid-fail").orElseThrow();
        assertTrue(memory.isEmpty());
    }

    // ==================== Metadata 可写 ====================

    @Test
    void should_observe_session_metadata_writable() {
        when(llmClient.chatWithTools(anyList(), anyList()))
                .thenReturn(new LlmResponse("答", "stop", null));

        // 通过 builder 模拟"agent 内部构造的 session",验证 metadata 行为
        AgentSession session = AgentSession.builder()
                .sessionId("sid-meta")
                .build();
        assertNotNull(session.tokenUsage());
        assertEquals(0L, session.tokenUsage().totalTokens());
        assertNotNull(session.metadataView());
        assertTrue(session.metadataView().isEmpty());

        // metadata put/get
        session.put("user-id", "alice");
        session.put("trace-source", "test");
        assertEquals("alice", session.get("user-id"));
        assertEquals("test", session.get("trace-source"));
        assertTrue(session.contains("user-id"));
        assertFalse(session.contains("missing"));
    }

    @Test
    void should_observe_token_usage_incrementable_on_session() {
        AgentSession session = AgentSession.builder().sessionId("s").build();
        session.tokenUsage().addPrompt(100);
        session.tokenUsage().addCompletion(50);
        assertEquals(150L, session.tokenUsage().totalTokens());
        assertEquals(100L, session.tokenUsage().promptTokens());
        assertEquals(50L, session.tokenUsage().completionTokens());
    }

    // ==================== traceId 一致性 ====================

    @Test
    void should_keep_result_trace_consistent_across_session() {
        when(llmClient.chatWithTools(anyList(), anyList()))
                .thenReturn(new LlmResponse("答", "stop", null));

        ToolCallingResult result = agent.answer("sid-2", "Q");

        // traceId 不为 null
        assertNotNull(result.trace().traceId());
        // trace 步骤来自 session 持有的同一个 AgentTrace 实例
        assertFalse(result.trace().isEmpty());
        assertTrue(result.trace().steps().size() >= 3); // USER_QUESTION + LLM_REQUEST + LLM_RESPONSE + FINAL_ANSWER
    }

    // ==================== 跨调用 session 隔离 ====================

    @Test
    void should_isolate_sessions_via_store() {
        when(llmClient.chatWithTools(anyList(), anyList()))
                .thenAnswer(inv -> new LlmResponse("A", "stop", null))
                .thenAnswer(inv -> new LlmResponse("B", "stop", null));

        agent.answer("sid-A", "Q1");
        agent.answer("sid-B", "Q1");

        AgentSession a = AgentSession.builder()
                .sessionId("sid-A")
                .memory(store.find("sid-A").orElseThrow())
                .build();
        AgentSession b = AgentSession.builder()
                .sessionId("sid-B")
                .memory(store.find("sid-B").orElseThrow())
                .build();
        assertEquals(1, a.memory().size());
        assertEquals(1, b.memory().size());
        assertEquals("Q1", a.memory().turns().get(0).question());
        assertEquals("Q1", b.memory().turns().get(0).question());
    }

    // ==================== 兼容性背书 ====================

    @Test
    void should_keep_public_api_unchanged() {
        when(llmClient.chatWithTools(anyList(), anyList()))
                .thenReturn(new LlmResponse("答", "stop", null));

        // 两个公开方法签名保持
        ToolCallingResult r1 = agent.answer("Q"); // 单参
        ToolCallingResult r2 = agent.answer("sid-3", "Q"); // 双参

        // 返回类型 + 字段均存在
        assertNotNull(r1);
        assertNotNull(r2);
        assertNotNull(r1.trace());
        assertNotNull(r2.trace());
        assertNotNull(r1.messages());
        assertNotNull(r2.messages());
        assertNotNull(r1.toolCalls());
    }

    @Test
    void should_reject_blank_sessionId_in_session_path() {
        // 公开签名校验保持
        assertThrows(IllegalArgumentException.class,
                () -> agent.answer(null, "Q"));
        assertThrows(IllegalArgumentException.class,
                () -> agent.answer("", "Q"));
        assertThrows(IllegalArgumentException.class,
                () -> agent.answer("  ", "Q"));
    }
}
