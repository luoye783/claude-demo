package com.example.claudedemo.agent.mcp;

import com.example.claudedemo.agent.ToolCallRecord;
import com.example.claudedemo.agent.ToolCallingExhaustedException;
import com.example.claudedemo.agent.ToolCallingResult;
import com.example.claudedemo.llm.ChatMessage;
import com.example.claudedemo.llm.LlmClient;
import com.example.claudedemo.llm.LlmResponse;
import com.example.claudedemo.llm.ToolCall;
import com.example.claudedemo.llm.ToolDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * {@link Nl2SqlMcpAgent} 单元测试：mock LlmClient + mock McpToolClient.
 *
 * <p>不依赖任何 Spring 上下文、数据库或真实 MCP Server，纯逻辑测试。
 *
 * @author claude-code
 * @since 0.0.1
 */
class Nl2SqlMcpAgentTest {

    private LlmClient llmClient;
    private McpToolClient mcpToolClient;
    private Nl2SqlMcpAgent agent;

    @BeforeEach
    void setUp() {
        llmClient = mock(LlmClient.class);
        mcpToolClient = mock(McpToolClient.class);
        agent = new Nl2SqlMcpAgent(llmClient, mcpToolClient);
    }

    // ==================== Happy path ====================

    @Test
    void should_call_get_schema_then_execute_sql_then_return_answer() {
        // round1: get_schema
        // round2: execute_sql
        // round3: final answer
        when(mcpToolClient.getSchema()).thenReturn("表 USERS(id INT, name VARCHAR)");
        when(mcpToolClient.executeSql("SELECT COUNT(*) FROM USERS"))
                .thenReturn("{\"sql\":\"SELECT COUNT(*) FROM USERS LIMIT 100\",\"rowCount\":1,\"rows\":[{\"3\":3}]}");

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

        // messages: system -> user -> assistant(tool_calls) -> tool -> assistant(tool_calls) -> tool -> assistant(answer)
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
        verify(mcpToolClient, times(1)).getSchema();
        verify(mcpToolClient, times(1)).executeSql("SELECT COUNT(*) FROM USERS");
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
        verifyNoMoreInteractions(mcpToolClient);
    }

    // ==================== Error handling ====================

    @Test
    void should_pass_error_from_mcp_client_back_to_llm() {
        // round1: get_schema 返回 Error
        // round2: LLM 重试或给出最终答案
        when(mcpToolClient.getSchema()).thenReturn("Error: [ConnectionRefused] MCP server not available");

        when(llmClient.chatWithTools(anyList(), anyList()))
                .thenReturn(new LlmResponse(null, "tool_calls", List.of(
                        toolCall("call_1", "get_schema", "{}")
                )))
                .thenReturn(new LlmResponse("无法连接数据库。", "stop", null));

        ToolCallingResult result = agent.answer("查一下用户表");

        assertEquals(2, result.rounds());
        String r = result.toolCalls().get(0).result();
        assertTrue(r.contains("Error"), "get_schema Error 应被传递,实际:" + r);
        assertTrue(r.contains("ConnectionRefused"), "应包含原始错误码,实际:" + r);

        // LLM 看到了 Error 信息
        List<ChatMessage> msgs = result.messages();
        assertEquals("tool", msgs.get(3).role());
        assertTrue(msgs.get(3).content().contains("Error"));
        assertEquals("call_1", msgs.get(3).toolCallId());
    }

    @Test
    void should_return_error_string_for_unknown_tool_name() {
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

    @Test
    void should_handle_malformed_arguments_json() {
        // 即使参数不是 JSON,execute_sql 也返回 Error
        when(llmClient.chatWithTools(anyList(), anyList()))
                .thenReturn(new LlmResponse(null, "tool_calls", List.of(
                        toolCall("call_1", "execute_sql", "not-a-json")
                )))
                .thenReturn(new LlmResponse("参数错误。", "stop", null));

        ToolCallingResult result = agent.answer("查一下");

        assertEquals(2, result.rounds());
        String r = result.toolCalls().get(0).result();
        assertTrue(r.contains("Error"), "无效参数 JSON 应返回 Error,实际:" + r);
        assertTrue(r.contains("InvalidArguments"), "应标注 InvalidArguments,实际:" + r);
        // McpToolClient 不应被调用(参数解析在 Agent 层失败)
        verify(mcpToolClient, times(0)).executeSql(anyString());
    }

    // ==================== Loop control ====================

    @Test
    void should_throw_when_exceeding_max_rounds() {
        // 5 轮全部返回 tool_calls,LLM 永远不收手
        when(mcpToolClient.getSchema()).thenReturn("schema info");
        when(llmClient.chatWithTools(anyList(), anyList()))
                .thenReturn(new LlmResponse(null, "tool_calls", List.of(
                        toolCall("call_loop", "get_schema", "{}")
                )));

        ToolCallingExhaustedException ex = assertThrows(
                ToolCallingExhaustedException.class,
                () -> agent.answer("查"));

        assertTrue(ex.getMessage().contains("5"),
                "异常 message 应含轮次,实际:" + ex.getMessage());
        verify(llmClient, times(Nl2SqlMcpAgent.MAX_ROUNDS)).chatWithTools(anyList(), anyList());
        assertEquals(5, ex.getToolCalls().size());
        assertEquals(2 + Nl2SqlMcpAgent.MAX_ROUNDS * 2, ex.getMessages().size());
    }

    // ==================== Tool definitions ====================

    @Test
    void should_pass_tool_definitions_to_llm() {
        when(llmClient.chatWithTools(anyList(), anyList()))
                .thenReturn(new LlmResponse("ok", "stop", null));

        agent.answer("hello");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ToolDefinition>> toolsCaptor = ArgumentCaptor.forClass(List.class);
        verify(llmClient, atLeastOnce()).chatWithTools(anyList(), toolsCaptor.capture());

        List<ToolDefinition> toolsSent = toolsCaptor.getValue();
        assertEquals(2, toolsSent.size());
        assertTrue(toolsSent.stream().anyMatch(t -> "get_schema".equals(t.name())));
        assertTrue(toolsSent.stream().anyMatch(t -> "execute_sql".equals(t.name())));

        // execute_sql 应声明 sql 参数
        ToolDefinition exec = toolsSent.stream()
                .filter(t -> "execute_sql".equals(t.name()))
                .findFirst().orElseThrow();
        assertTrue(exec.description().contains("SELECT"));
        assertNotNull(exec.parameters());
        assertEquals("object", exec.parameters().get("type"));
    }

    // ==================== Tool call records ====================

    @Test
    void should_record_full_tool_call_records_in_result() {
        when(mcpToolClient.getSchema()).thenReturn("表 USERS(id INT, name VARCHAR)");
        when(mcpToolClient.executeSql("SELECT * FROM USERS"))
                .thenReturn("{\"sql\":\"SELECT * FROM USERS LIMIT 100\",\"rowCount\":2,\"rows\":[{\"ID\":1,\"NAME\":\"Alice\"},{\"ID\":2,\"NAME\":\"Bob\"}]}");

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
        assertTrue(r0.result().contains("USERS"), "schema 文本应含 USERS,实际:" + r0.result());

        ToolCallRecord r1 = result.toolCalls().get(1);
        assertEquals("id-B", r1.toolCallId());
        assertEquals("execute_sql", r1.toolName());
        assertTrue(r1.arguments().contains("SELECT * FROM USERS"));
        assertTrue(r1.result().contains("Alice"), "rows JSON 应含 Alice,实际:" + r1.result());
    }

    // ==================== helpers ====================

    private static ToolCall toolCall(String id, String name, String arguments) {
        return new ToolCall(id, "function", new ToolCall.Function(name, arguments));
    }
}
