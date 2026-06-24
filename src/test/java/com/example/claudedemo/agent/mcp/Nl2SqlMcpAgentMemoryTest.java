package com.example.claudedemo.agent.mcp;

import com.example.claudedemo.agent.ToolCallingExhaustedException;
import com.example.claudedemo.agent.ToolCallingResult;
import com.example.claudedemo.agent.memory.ConversationMemory;
import com.example.claudedemo.agent.memory.ConversationStore;
import com.example.claudedemo.agent.memory.ConversationTurn;
import com.example.claudedemo.agent.memory.InMemoryConversationStore;
import com.example.claudedemo.agent.trace.StepType;
import com.example.claudedemo.agent.trace.TraceStep;
import com.example.claudedemo.llm.ChatMessage;
import com.example.claudedemo.llm.LlmClient;
import com.example.claudedemo.llm.LlmResponse;
import com.example.claudedemo.llm.ToolCall;
import com.example.claudedemo.llm.ToolDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link Nl2SqlMcpAgent} 短期会话记忆集成测试.
 *
 * <p>验证 {@link ConversationStore} 与 Agent 的交互：同 sessionId 历史传递、
 * 不同 sessionId 隔离、FIFO 裁剪、异常不写记忆、旧 answer 方法兼容等。
 *
 * @since 0.0.1
 */
class Nl2SqlMcpAgentMemoryTest {

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
    private ConversationStore store;
    private Nl2SqlMcpAgent agent;

    @BeforeEach
    void setUp() {
        llmClient = mock(LlmClient.class);
        mcpToolClient = mock(McpToolClient.class);
        lenient().when(mcpToolClient.listTools()).thenReturn(FAKE_TOOL_DEFS);
        store = new InMemoryConversationStore();
        agent = new Nl2SqlMcpAgent(llmClient, mcpToolClient, store);
    }

    // ==================== 同 sessionId 记忆保持 ====================

    @Test
    void should_keep_memory_across_calls_with_same_sessionId() {
        when(llmClient.chatWithTools(anyList(), anyList()))
                .thenReturn(new LlmResponse("First answer", "stop", null))
                .thenReturn(new LlmResponse("Second answer", "stop", null));

        ToolCallingResult r1 = agent.answer("session-1", "What tables exist?");
        ToolCallingResult r2 = agent.answer("session-1", "Show me users");

        // 第 1 次调用的 messages: system + user + assistant
        assertEquals(3, r1.messages().size());
        assertEquals("user", r1.messages().get(1).role());
        assertEquals("What tables exist?", r1.messages().get(1).content());

        // 第 2 次调用的 messages: system + history(user+assistant) + user + assistant
        assertEquals(5, r2.messages().size());
        // history 部分
        assertTrue(r2.messages().stream().anyMatch(m ->
                m.role().equals("user") && "What tables exist?".equals(m.content())),
                "第 2 次调用应包含第 1 次的用户问题");
        assertTrue(r2.messages().stream().anyMatch(m ->
                m.role().equals("assistant") && "First answer".equals(m.content())),
                "第 2 次调用应包含第 1 次的最终答案");
    }

    @Test
    void should_maintain_memory_across_multiple_calls() {
        when(llmClient.chatWithTools(anyList(), anyList()))
                .thenReturn(new LlmResponse("A1", "stop", null))
                .thenReturn(new LlmResponse("A2", "stop", null))
                .thenReturn(new LlmResponse("A3", "stop", null));

        agent.answer("sid-multi", "Q1");
        agent.answer("sid-multi", "Q2");
        agent.answer("sid-multi", "Q3");

        // memory 应有 3 个 turn
        ConversationMemory memory = store.find("sid-multi").orElseThrow();
        assertEquals(3, memory.size());

        // toChatMessages 应包含所有历史
        List<ChatMessage> history = memory.toChatMessages();
        assertEquals(6, history.size()); // 3 turns × 2 messages
        // index: 0=user(Q1), 1=assistant(A1), 2=user(Q2), 3=assistant(A2), 4=user(Q3), 5=assistant(A3)
        assertEquals("user", history.get(0).role());
        assertEquals("Q1", history.get(0).content());
        assertEquals("assistant", history.get(1).role());
        assertEquals("A1", history.get(1).content());
        assertEquals("user", history.get(4).role());
        assertEquals("Q3", history.get(4).content());
        assertEquals("assistant", history.get(5).role());
        assertEquals("A3", history.get(5).content());
    }

    // ==================== 不同 sessionId 隔离 ====================

    @Test
    void should_isolate_memory_between_different_sessionIds() {
        when(llmClient.chatWithTools(anyList(), anyList()))
                .thenReturn(new LlmResponse("Answer A", "stop", null))
                .thenReturn(new LlmResponse("Answer B", "stop", null));

        agent.answer("session-A", "Question for A");
        agent.answer("session-B", "Question for B");

        // A 有 1 个 turn
        ConversationMemory memoryA = store.find("session-A").orElseThrow();
        assertEquals(1, memoryA.size());
        assertEquals("Question for A", memoryA.turns().get(0).question());

        // B 也有 1 个 turn, 互不干扰
        ConversationMemory memoryB = store.find("session-B").orElseThrow();
        assertEquals(1, memoryB.size());
        assertEquals("Question for B", memoryB.turns().get(0).question());

        // store 中应有 2 个 session
        assertEquals(2, store.size());
    }

    // ==================== 超过 20 条自动裁剪 ====================

    @Test
    void should_auto_truncate_messages_beyond_max_20() {
        // 每次返回直接答案（不调工具）
        when(llmClient.chatWithTools(anyList(), anyList()))
                .thenAnswer(invocation -> {
                    List<ChatMessage> msgs = invocation.getArgument(0);
                    String lastQuestion = msgs.get(msgs.size() - 1).content();
                    return new LlmResponse("Answer to: " + lastQuestion, "stop", null);
                });

        // 连续调用 25 轮
        for (int i = 1; i <= ConversationMemory.MAX_TURNS + 5; i++) {
            agent.answer("session-trunc", "Question " + i);
        }

        ConversationMemory memory = store.find("session-trunc").orElseThrow();
        // 不超过 MAX_TURNS
        assertTrue(memory.size() <= ConversationMemory.MAX_TURNS,
                "memory 不应超过 " + ConversationMemory.MAX_TURNS + ", 实际: " + memory.size());

        // 最旧的 5 个被裁掉, 第一个 turn 应该是 Question 6
        List<ChatMessage> history = memory.toChatMessages();
        assertEquals("user", history.get(0).role());
        assertTrue(history.get(0).content().contains("Question 6"),
                "最旧消息应已被裁剪, 首个历史消息为: " + history.get(0).content());
    }

    // ==================== 新 sessionId 自动创建 ====================

    @Test
    void should_auto_create_memory_for_new_sessionId() {
        when(llmClient.chatWithTools(anyList(), anyList()))
                .thenReturn(new LlmResponse("welcome", "stop", null));

        agent.answer("brand-new-session", "Hello");

        assertTrue(store.find("brand-new-session").isPresent());
        assertEquals(1, store.find("brand-new-session").get().size());
    }

    // ==================== 空白 sessionId 拒绝 ====================

    @Test
    void should_reject_blank_sessionId() {
        assertThrows(IllegalArgumentException.class,
                () -> agent.answer(null, "question"));
        assertThrows(IllegalArgumentException.class,
                () -> agent.answer("", "question"));
        assertThrows(IllegalArgumentException.class,
                () -> agent.answer("  ", "question"));
    }

    // ==================== 异常不写记忆 ====================

    @Test
    void should_not_write_memory_when_throwing_exhausted_exception() {
        when(mcpToolClient.callTool(eq("get_schema"), anyString()))
                .thenReturn("schema info");
        when(llmClient.chatWithTools(anyList(), anyList()))
                .thenReturn(new LlmResponse(null, "tool_calls", List.of(
                        toolCall("call_loop", "get_schema", "{}")
                )));

        // 第一次调用耗尽轮次
        assertThrows(ToolCallingExhaustedException.class,
                () -> agent.answer("session-exhaust", "查"));

        // memory 应是空的（异常不写）
        ConversationMemory memory = store.find("session-exhaust").orElseThrow();
        assertTrue(memory.isEmpty(), "异常后 memory 应为空, 实际: " + memory.size());

        // 第二次调用成功, memory 正常记录
        when(llmClient.chatWithTools(anyList(), anyList()))
                .thenReturn(new LlmResponse("成功了", "stop", null));
        agent.answer("session-exhaust", "再查一次");

        assertEquals(1, memory.size(), "成功调用后 memory 应有 1 个 turn");
        assertEquals("再查一次", memory.turns().get(0).question());
    }

    // ==================== 旧 answer 不污染 store ====================

    @Test
    void should_not_pollute_store_when_answer_without_sessionId() {
        when(llmClient.chatWithTools(anyList(), anyList()))
                .thenReturn(new LlmResponse("no memory answer", "stop", null));

        agent.answer("旧方法调用");

        // store 应为空（旧方法不写 memory）
        assertEquals(0, store.size());
    }

    @Test
    void should_not_pollute_store_when_answer_without_sessionId_with_tool_calls() {
        when(mcpToolClient.callTool(eq("get_schema"), anyString()))
                .thenReturn("table info");
        when(llmClient.chatWithTools(anyList(), anyList()))
                .thenReturn(new LlmResponse(null, "tool_calls", List.of(
                        toolCall("c1", "get_schema", "{}")
                )))
                .thenReturn(new LlmResponse("done", "stop", null));

        agent.answer("旧方法带工具调用");

        // store 仍应为空
        assertEquals(0, store.size());
    }

    // ==================== Trace 兼容性 ====================

    @Test
    void should_keep_trace_compatible_with_memory() {
        when(mcpToolClient.callTool(eq("get_schema"), anyString()))
                .thenReturn("table info");
        when(llmClient.chatWithTools(anyList(), anyList()))
                .thenReturn(new LlmResponse(null, "tool_calls", List.of(
                        toolCall("c1", "get_schema", "{}")
                )))
                .thenReturn(new LlmResponse("最终答案", "stop", null));

        ToolCallingResult result = agent.answer("session-trace", "trace 测试");

        // trace 应完整: USER_QUESTION → LLM_REQUEST → LLM_RESPONSE → TOOL_CALL → TOOL_RESULT → LLM_REQUEST → LLM_RESPONSE → FINAL_ANSWER
        assertNotNull(result.trace());
        assertFalse(result.trace().isEmpty());
        assertNotNull(result.trace().traceId());

        List<StepType> expectedOrder = List.of(
                StepType.USER_QUESTION,
                StepType.LLM_REQUEST, StepType.LLM_RESPONSE,
                StepType.TOOL_CALL, StepType.TOOL_RESULT,
                StepType.LLM_REQUEST, StepType.LLM_RESPONSE,
                StepType.FINAL_ANSWER
        );
        List<StepType> actualOrder = result.trace().steps().stream()
                .map(TraceStep::stepType)
                .toList();
        assertEquals(expectedOrder, actualOrder);

        // trace 仍记录当前请求轨迹
        assertEquals("trace 测试", result.trace().steps().get(0).content());
        assertEquals("最终答案", result.trace().steps().get(result.trace().steps().size() - 1).content());
    }

    // ==================== Memory 不保存中间状态 ====================

    @Test
    void should_not_store_tool_calls_or_sql_results_in_memory() {
        when(mcpToolClient.callTool(eq("get_schema"), anyString()))
                .thenReturn("表 USERS(id INT)");
        when(mcpToolClient.callTool(eq("execute_sql"), anyString()))
                .thenReturn("{\"rowCount\":2,\"rows\":[{\"ID\":1},{\"ID\":2}]}");

        when(llmClient.chatWithTools(anyList(), anyList()))
                .thenReturn(new LlmResponse(null, "tool_calls", List.of(
                        toolCall("c1", "get_schema", "{}")
                )))
                .thenReturn(new LlmResponse(null, "tool_calls", List.of(
                        toolCall("c2", "execute_sql", "{\"sql\":\"SELECT ID FROM USERS\"}")
                )))
                .thenReturn(new LlmResponse("共有 2 个用户。", "stop", null));

        agent.answer("session-clean", "有多少用户？");

        ConversationMemory memory = store.find("session-clean").orElseThrow();
        assertEquals(1, memory.size());

        ConversationTurn turn = memory.turns().get(0);
        assertEquals("有多少用户？", turn.question());
        assertEquals("共有 2 个用户。", turn.answer());

        // memory 中不应包含 tool_call / tool_result / schema 相关内容
        assertFalse(turn.question().contains("get_schema"));
        assertFalse(turn.answer().contains("execute_sql"));
        assertFalse(turn.answer().contains("rowCount"));
    }

    // ==================== 新 answer 方法的 messages 仍包含完整对话 ====================

    @Test
    void should_include_full_conversation_in_result_messages_with_memory() {
        when(llmClient.chatWithTools(anyList(), anyList()))
                .thenReturn(new LlmResponse("First answer", "stop", null));

        ToolCallingResult result = agent.answer("session-full", "First question");

        // result 的 messages 包含本轮完整对话: system + user + assistant
        assertEquals(3, result.messages().size());
        assertEquals("system", result.messages().get(0).role());
        assertEquals("user", result.messages().get(1).role());
        assertEquals("First question", result.messages().get(1).content());
        assertEquals("assistant", result.messages().get(2).role());
        assertEquals("First answer", result.messages().get(2).content());

        // memory 只保存问答(不含 tool 中间状态)
        ConversationMemory memory = store.find("session-full").orElseThrow();
        assertEquals(1, memory.size());
        assertEquals("First question", memory.turns().get(0).question());
        assertEquals("First answer", memory.turns().get(0).answer());
    }

    // ==================== helpers ====================

    private static ToolCall toolCall(String id, String name, String arguments) {
        return new ToolCall(id, "function", new ToolCall.Function(name, arguments));
    }
}
