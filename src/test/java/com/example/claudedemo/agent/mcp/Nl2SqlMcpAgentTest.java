package com.example.claudedemo.agent.mcp;

import com.example.claudedemo.agent.ToolCallRecord;
import com.example.claudedemo.agent.ToolCallingExhaustedException;
import com.example.claudedemo.agent.ToolCallingResult;
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
import org.mockito.ArgumentCaptor;

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
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link Nl2SqlMcpAgent} 单元测试:mock LlmClient + mock McpToolClient.
 *
 * <p>不依赖任何 Spring 上下文、数据库或真实 MCP Server,纯逻辑测试.
 *
 * <p><b>关于 listTools mock 位置</b>:Agent 在构造期即拉取 tool defs 并缓存,
 * 因此默认 stub 在 {@code @BeforeEach} 中完成({@code lenient()} 标记,允许部分测试
 * 用空列表 stub 覆盖并重建 agent).
 *
 * @author claude-code
 * @since 0.0.1
 */
class Nl2SqlMcpAgentTest {

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
    private Nl2SqlMcpAgent agent;

    @BeforeEach
    void setUp() {
        llmClient = mock(LlmClient.class);
        mcpToolClient = mock(McpToolClient.class);
        // listTools() 在 Agent 构造期调用;lenient() 允许部分测试覆盖为空列表
        lenient().when(mcpToolClient.listTools()).thenReturn(FAKE_TOOL_DEFS);
        agent = new Nl2SqlMcpAgent(llmClient, mcpToolClient, new InMemoryConversationStore());
    }

    // ==================== Happy path ====================

    @Test
    void should_call_get_schema_then_execute_sql_then_return_answer() {
        when(mcpToolClient.callTool(eq("get_schema"), anyString()))
                .thenReturn("表 USERS(id INT, name VARCHAR)");
        when(mcpToolClient.callTool(eq("execute_sql"), anyString()))
                .thenReturn("{\"sql\":\"SELECT COUNT(*) FROM USERS LIMIT 100\","
                        + "\"rowCount\":1,\"rows\":[{\"3\":3}]}");

        when(llmClient.chatWithTools(anyList(), anyList()))
                .thenReturn(new LlmResponse(null, "tool_calls", List.of(
                        toolCall("call_1", "get_schema", "{}")
                )))
                .thenReturn(new LlmResponse(null, "tool_calls", List.of(
                        toolCall("call_2", "execute_sql", "{\"sql\":\"SELECT COUNT(*) FROM USERS\"}")
                )))
                .thenReturn(new LlmResponse("共有 3 个用户。", "stop", null));

        ToolCallingResult result = agent.answer("用户表有多少人？");

        assertEquals("用户表有多少人？", result.question());
        assertEquals("共有 3 个用户。", result.answer());
        assertEquals(3, result.rounds());
        assertEquals(2, result.toolCalls().size());
        assertEquals("get_schema", result.toolCalls().get(0).toolName());
        assertEquals("execute_sql", result.toolCalls().get(1).toolName());

        List<ChatMessage> msgs = result.messages();
        assertEquals(7, msgs.size());
        assertEquals("system", msgs.get(0).role());
        assertEquals("user", msgs.get(1).role());
        assertEquals("assistant", msgs.get(2).role());
        assertNotNull(msgs.get(2).toolCalls());
        assertEquals("tool", msgs.get(3).role());
        assertEquals("call_1", msgs.get(3).toolCallId());
        assertEquals("assistant", msgs.get(4).role());
        assertNotNull(msgs.get(4).toolCalls());
        assertEquals("tool", msgs.get(5).role());
        assertEquals("call_2", msgs.get(5).toolCallId());
        assertEquals("assistant", msgs.get(6).role());
        assertNull(msgs.get(6).toolCalls());

        verify(llmClient, times(3)).chatWithTools(anyList(), anyList());
        verify(mcpToolClient, times(1)).callTool("get_schema", "{}");
        verify(mcpToolClient, times(1))
                .callTool("execute_sql", "{\"sql\":\"SELECT COUNT(*) FROM USERS\"}");
    }

    @Test
    void should_return_directly_when_llm_answers_without_calling_tools() {
        when(llmClient.chatWithTools(anyList(), anyList()))
                .thenReturn(new LlmResponse("数据库是空的。", "stop", null));

        ToolCallingResult result = agent.answer("数据库有什么？");

        assertEquals("数据库是空的。", result.answer());
        assertEquals(1, result.rounds());
        assertTrue(result.toolCalls().isEmpty());
        assertEquals(3, result.messages().size());
        verify(llmClient, times(1)).chatWithTools(anyList(), anyList());
        // listTools 仅在构造期被调用 1 次,answer 过程不再触发
        verify(mcpToolClient, times(1)).listTools();
        verifyNoMoreInteractions(mcpToolClient);
    }

    // ==================== Error handling ====================

    @Test
    void should_pass_error_from_mcp_client_back_to_llm() {
        when(mcpToolClient.callTool(eq("get_schema"), anyString()))
                .thenReturn("Error: [ConnectionRefused] MCP server not available");

        when(llmClient.chatWithTools(anyList(), anyList()))
                .thenReturn(new LlmResponse(null, "tool_calls", List.of(
                        toolCall("call_1", "get_schema", "{}")
                )))
                .thenReturn(new LlmResponse("无法连接数据库。", "stop", null));

        ToolCallingResult result = agent.answer("查一下用户表");

        assertEquals(2, result.rounds());
        String r = result.toolCalls().get(0).result();
        assertTrue(r.contains("Error"), "Error 应被传递,实际:" + r);
        assertTrue(r.contains("ConnectionRefused"), "应包含原始错误码,实际:" + r);

        List<ChatMessage> msgs = result.messages();
        assertEquals("tool", msgs.get(3).role());
        assertTrue(msgs.get(3).content().contains("Error"));
        assertEquals("call_1", msgs.get(3).toolCallId());
    }

    @Test
    void should_return_error_string_for_unknown_tool_name() {
        when(mcpToolClient.callTool(eq("non_existent_tool"), anyString()))
                .thenReturn("Error: [UnknownTool] unknown tool 'non_existent_tool'");

        when(llmClient.chatWithTools(anyList(), anyList()))
                .thenReturn(new LlmResponse(null, "tool_calls", List.of(
                        toolCall("call_x", "non_existent_tool", "{}")
                )))
                .thenReturn(new LlmResponse("我没法做这件事。", "stop", null));

        ToolCallingResult result = agent.answer("做某事");

        assertEquals(2, result.rounds());
        String r = result.toolCalls().get(0).result();
        assertTrue(r.contains("Error"), "未知工具应返回 Error,实际:" + r);
        assertTrue(r.contains("UnknownTool"), "应标注 UnknownTool,实际:" + r);
        assertTrue(r.contains("non_existent_tool"), "应回显工具名,实际:" + r);
    }

    // ==================== Loop control ====================

    @Test
    void should_throw_when_exceeding_max_rounds() {
        when(mcpToolClient.callTool(eq("get_schema"), anyString()))
                .thenReturn("schema info");

        when(llmClient.chatWithTools(anyList(), anyList()))
                .thenReturn(new LlmResponse(null, "tool_calls", List.of(
                        toolCall("call_loop", "get_schema", "{}")
                )));

        ToolCallingExhaustedException ex = assertThrows(
                ToolCallingExhaustedException.class,
                () -> agent.answer("查"));

        assertTrue(ex.getMessage().contains("5"));
        verify(llmClient, times(Nl2SqlMcpAgent.MAX_ROUNDS)).chatWithTools(anyList(), anyList());
        assertEquals(5, ex.getToolCalls().size());
        assertEquals(2 + Nl2SqlMcpAgent.MAX_ROUNDS * 2, ex.getMessages().size());
    }

    // ==================== Tool definitions (V2: 动态拉取) ====================

    @Test
    void should_get_tool_definitions_from_mcp_client() {
        when(llmClient.chatWithTools(anyList(), anyList()))
                .thenReturn(new LlmResponse("ok", "stop", null));

        agent.answer("hello");

        // 验证 tool definitions 来自 McpToolClient 并传给了 LLM
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ToolDefinition>> toolsCaptor = ArgumentCaptor.forClass(List.class);
        verify(llmClient, atLeastOnce()).chatWithTools(anyList(), toolsCaptor.capture());

        List<ToolDefinition> toolsSent = toolsCaptor.getValue();
        assertEquals(2, toolsSent.size());
        assertTrue(toolsSent.stream().anyMatch(t -> "get_schema".equals(t.name())));
        assertTrue(toolsSent.stream().anyMatch(t -> "execute_sql".equals(t.name())));
        // 构造期已拉取,answer 过程不再重复调用
        verify(mcpToolClient, times(1)).listTools();
    }

    // ==================== Tool call records ====================

    @Test
    void should_record_full_tool_call_records_in_result() {
        when(mcpToolClient.callTool(eq("get_schema"), anyString()))
                .thenReturn("表 USERS(id INT, name VARCHAR)");
        when(mcpToolClient.callTool(eq("execute_sql"), anyString()))
                .thenReturn("{\"sql\":\"SELECT * FROM USERS LIMIT 100\","
                        + "\"rowCount\":2,\"rows\":[{\"ID\":1,\"NAME\":\"Alice\"},"
                        + "{\"ID\":2,\"NAME\":\"Bob\"}]}");

        when(llmClient.chatWithTools(anyList(), anyList()))
                .thenReturn(new LlmResponse(null, "tool_calls", List.of(
                        toolCall("id-A", "get_schema", "{}")
                )))
                .thenReturn(new LlmResponse(null, "tool_calls", List.of(
                        toolCall("id-B", "execute_sql", "{\"sql\":\"SELECT * FROM USERS\"}")
                )))
                .thenReturn(new LlmResponse("done", "stop", null));

        ToolCallingResult result = agent.answer("list");

        ToolCallRecord r0 = result.toolCalls().get(0);
        assertEquals("id-A", r0.toolCallId());
        assertEquals("get_schema", r0.toolName());
        assertEquals("{}", r0.arguments());
        assertFalse(r0.result().isEmpty());
        assertTrue(r0.result().contains("USERS"));

        ToolCallRecord r1 = result.toolCalls().get(1);
        assertEquals("id-B", r1.toolCallId());
        assertEquals("execute_sql", r1.toolName());
        assertTrue(r1.arguments().contains("SELECT * FROM USERS"));
        assertTrue(r1.result().contains("Alice"));
    }

    // ==================== Trace 验证(V1 已有断言零变更) ====================

    @Test
    void should_record_complete_trace_with_correct_order() {
        when(mcpToolClient.callTool(eq("get_schema"), anyString()))
                .thenReturn("表 USERS(id INT, name VARCHAR)");
        when(mcpToolClient.callTool(eq("execute_sql"), anyString()))
                .thenReturn("{\"rowCount\":1,\"rows\":[{\"3\":3}]}");

        when(llmClient.chatWithTools(anyList(), anyList()))
                .thenReturn(new LlmResponse(null, "tool_calls", List.of(
                        toolCall("call_1", "get_schema", "{}")
                )))
                .thenReturn(new LlmResponse(null, "tool_calls", List.of(
                        toolCall("call_2", "execute_sql", "{\"sql\":\"SELECT COUNT(*) FROM USERS\"}")
                )))
                .thenReturn(new LlmResponse("共有 3 个用户。", "stop", null));

        ToolCallingResult result = agent.answer("用户表有多少人？");

        // trace 存在且 traceId 非空
        assertNotNull(result.trace());
        assertFalse(result.trace().isEmpty());
        assertNotNull(result.trace().traceId());
        assertFalse(result.trace().traceId().isBlank());

        // 步骤顺序:USER_QUESTION → LLM_REQUEST → LLM_RESPONSE → TOOL_CALL → TOOL_RESULT ...
        List<StepType> expectedOrder = List.of(
                StepType.USER_QUESTION,
                StepType.LLM_REQUEST, StepType.LLM_RESPONSE,
                StepType.TOOL_CALL, StepType.TOOL_RESULT,
                StepType.LLM_REQUEST, StepType.LLM_RESPONSE,
                StepType.TOOL_CALL, StepType.TOOL_RESULT,
                StepType.LLM_REQUEST, StepType.LLM_RESPONSE,
                StepType.FINAL_ANSWER
        );
        List<StepType> actualOrder = result.trace().steps().stream()
                .map(TraceStep::stepType)
                .toList();
        assertEquals(expectedOrder, actualOrder);

        // stepNo 单调递增从 1 开始
        List<Integer> stepNos = result.trace().steps().stream()
                .map(TraceStep::stepNo)
                .toList();
        for (int i = 0; i < stepNos.size(); i++) {
            assertEquals(i + 1, stepNos.get(i), "stepNo 应从 1 连续递增");
        }

        // USER_QUESTION 内容是用户问题
        assertEquals("用户表有多少人？", result.trace().steps().get(0).content());
        // FINAL_ANSWER 内容是答案
        TraceStep finalStep = result.trace().steps().get(
                result.trace().steps().size() - 1);
        assertEquals("共有 3 个用户。", finalStep.content());

        // 额外验证 LLM_REQUEST 步骤存在(方案对 MCP Agent 的特别要求)
        assertTrue(actualOrder.contains(StepType.LLM_REQUEST),
                "MCP Agent trace 应包含 LLM_REQUEST 步骤");

        // 耗时步骤字段均存在
        result.trace().steps().forEach(step -> {
            assertTrue(step.timestampMs() > 0, "timestampMs 应已设置,实际:" + step.timestampMs());
            assertNotNull(step.content(), "content 字段不应为 null");
            assertTrue(step.durationMs() >= 0, "durationMs 不应为负,实际:" + step.durationMs());
        });
    }

    @Test
    void should_record_error_step_when_exceeding_max_rounds() {
        when(mcpToolClient.callTool(eq("get_schema"), anyString()))
                .thenReturn("schema info");

        when(llmClient.chatWithTools(anyList(), anyList()))
                .thenReturn(new LlmResponse(null, "tool_calls", List.of(
                        toolCall("call_loop", "get_schema", "{}")
                )));

        ToolCallingExhaustedException ex = assertThrows(
                ToolCallingExhaustedException.class,
                () -> agent.answer("查"));

        // 异常携带的 trace 含 ERROR 步骤
        assertNotNull(ex.getTrace());
        assertNotNull(ex.getTrace().traceId());
        List<TraceStep> steps = ex.getTrace().steps();
        assertFalse(steps.isEmpty());
        assertEquals(StepType.ERROR, steps.get(steps.size() - 1).stepType());
        assertTrue(steps.get(steps.size() - 1).content().contains("5"),
                "ERROR 内容应说明超过最大轮数 5,实际:" + steps.get(steps.size() - 1).content());
    }

    // ==================== V2 新增:动态发现 / 构造期拉取 ====================

    @Test
    void should_load_tools_at_construction_time() {
        // setUp() 中已创建 agent,此处断言构造期行为:
        // 1) listTools 在构造期被调 1 次
        // 2) 多次 answer() 不会重复调 listTools(已缓存)
        verify(mcpToolClient, times(1)).listTools();

        when(llmClient.chatWithTools(anyList(), anyList()))
                .thenReturn(new LlmResponse("ok1", "stop", null))
                .thenReturn(new LlmResponse("ok2", "stop", null));

        agent.answer("q1");
        agent.answer("q2");

        // 仍然只 1 次 — answer 过程不再触发 listTools
        verify(mcpToolClient, times(1)).listTools();
    }

    @Test
    void should_use_dynamic_tool_definitions_for_llm() {
        // 关键场景:listTools() 动态返回 [get_schema, execute_sql],
        // 验证传给 LLM 的 tool definitions 恰好是这两个,顺序与 MCP Server 一致
        when(llmClient.chatWithTools(anyList(), anyList()))
                .thenReturn(new LlmResponse("done", "stop", null));

        agent.answer("hello");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ToolDefinition>> captor = ArgumentCaptor.forClass(List.class);
        verify(llmClient, atLeastOnce()).chatWithTools(anyList(), captor.capture());

        List<ToolDefinition> sent = captor.getValue();
        assertEquals(2, sent.size());
        // 顺序与 MCP 返回顺序一致(FAKE_TOOL_DEFS: get_schema 在前)
        assertEquals("get_schema", sent.get(0).name());
        assertEquals("execute_sql", sent.get(1).name());
        // description 完整传递(非空且与 FAKE_TOOL_DEFS 一致)
        assertTrue(sent.get(0).description().contains("表"),
                "get_schema description 应包含 '表',实际:" + sent.get(0).description());
        assertTrue(sent.get(1).description().contains("SELECT"),
                "execute_sql description 应包含 'SELECT',实际:" + sent.get(1).description());
        // parameters 完整传递
        assertEquals("object", sent.get(0).parameters().get("type"));
        assertEquals("object", sent.get(1).parameters().get("type"));
        @SuppressWarnings("unchecked")
        Map<String, Object> execProps = (Map<String, Object>) sent.get(1).parameters().get("properties");
        assertNotNull(execProps);
        assertTrue(execProps.containsKey("sql"), "execute_sql 应声明 sql 参数");
    }

    @Test
    void should_log_warning_and_continue_when_no_tools_discovered() {
        // 覆盖 setUp 的 stub:返回空列表
        when(mcpToolClient.listTools()).thenReturn(List.of());
        // 重建 agent — 构造期拉取空列表,只 log.warn 不抛
        Nl2SqlMcpAgent emptyAgent = new Nl2SqlMcpAgent(llmClient, mcpToolClient);

        // Agent 仍可正常工作(LLM 在没有工具的情况下应直接给答案)
        when(llmClient.chatWithTools(anyList(), anyList()))
                .thenReturn(new LlmResponse("抱歉,无可用工具。", "stop", null));

        ToolCallingResult result = emptyAgent.answer("查");

        assertNotNull(result);
        assertEquals("抱歉,无可用工具。", result.answer());
        assertEquals(1, result.rounds());
        // setUp 中 agent 调 1 次 + 本测试 emptyAgent 调 1 次 = 2 次;
        // 关键断言:emptyAgent 的 answer 过程不重复调 listTools(已缓存)
        verify(mcpToolClient, times(2)).listTools();
        emptyAgent.answer("再问");
        verify(mcpToolClient, times(2)).listTools();
    }

    // ==================== helpers ====================

    private static ToolCall toolCall(String id, String name, String arguments) {
        return new ToolCall(id, "function", new ToolCall.Function(name, arguments));
    }
}
