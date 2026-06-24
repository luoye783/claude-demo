package com.example.claudedemo.agent;

import com.example.claudedemo.agent.tools.AgentTool;
import com.example.claudedemo.agent.tools.ExecuteSqlTool;
import com.example.claudedemo.agent.tools.GetSchemaTool;
import com.example.claudedemo.agent.trace.StepType;
import com.example.claudedemo.agent.trace.TraceStep;
import com.example.claudedemo.llm.ChatMessage;
import com.example.claudedemo.llm.LlmClient;
import com.example.claudedemo.llm.LlmResponse;
import com.example.claudedemo.llm.ToolCall;
import com.example.claudedemo.llm.ToolDefinition;
import com.example.claudedemo.sql.SchemaIntrospector;
import com.example.claudedemo.sql.SqlExecutor;
import com.example.claudedemo.sql.SqlValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

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
import static org.mockito.Mockito.when;

/**
 * Nl2SqlToolAgent 测试：H2 + 真 SchemaSelector/SqlValidator/SqlExecutor + 真工具实现 + mock LlmClient.
 *
 * <p>策略与 {@link Nl2SqlAgentTest} 一致：Java 26 上 Mockito 5.11 mock SchemaSelector 会失败,
 * 所以 SchemaSelector / SqlExecutor / SqlValidator / 工具 全部用真 H2 实例,只 mock LlmClient。
 */
@JdbcTest
@Import({SchemaIntrospector.class, com.example.claudedemo.agent.SchemaSelector.class,
        SqlExecutor.class, SqlValidator.class,
        GetSchemaTool.class, ExecuteSqlTool.class})
class Nl2SqlToolAgentTest {

    @Autowired private com.example.claudedemo.agent.SchemaSelector schemaSelector;
    @Autowired private SqlValidator sqlValidator;
    @Autowired private SqlExecutor sqlExecutor;
    @Autowired private JdbcTemplate jdbcTemplate;

    private LlmClient llmClient;
    private Nl2SqlToolAgent agent;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS USERS");
        jdbcTemplate.execute("CREATE TABLE USERS (id INT NOT NULL, name VARCHAR(50), age INT)");
        jdbcTemplate.update("INSERT INTO USERS VALUES (1, 'Alice', 30)");
        jdbcTemplate.update("INSERT INTO USERS VALUES (2, 'Bob', 25)");
        jdbcTemplate.update("INSERT INTO USERS VALUES (3, 'Charlie', 35)");

        llmClient = mock(LlmClient.class);
        List<AgentTool> tools = List.of(
                new GetSchemaTool(schemaSelector),
                new ExecuteSqlTool(sqlValidator, sqlExecutor)
        );
        agent = new Nl2SqlToolAgent(llmClient, tools);
    }

    // ==================== Happy path ====================

    @Test
    void should_call_get_schema_then_execute_sql_then_return_answer() {
        // round1: get_schema
        // round2: execute_sql
        // round3: final answer
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

        // messages 顺序: system -> user -> assistant(tool_calls) -> tool -> assistant(tool_calls) -> tool -> assistant(answer)
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
    }

    @Test
    void should_return_directly_when_llm_answers_without_calling_tools() {
        when(llmClient.chatWithTools(anyList(), anyList()))
                .thenReturn(new LlmResponse("数据库是空的。", "stop", null));

        ToolCallingResult result = agent.answer("数据库有什么？");

        assertEquals("数据库是空的。", result.answer());
        assertEquals(1, result.rounds());
        assertTrue(result.toolCalls().isEmpty());
        // messages: system + user + assistant
        assertEquals(3, result.messages().size());
        verify(llmClient, times(1)).chatWithTools(anyList(), anyList());
    }

    // ==================== Tool error & self-repair ====================

    @Test
    void should_self_repair_when_execute_sql_returns_error() {
        // round1: get_schema
        // round2: execute_sql(坏 SQL,validator 拒)
        // round3: execute_sql(好 SQL)
        // round4: final answer
        when(llmClient.chatWithTools(anyList(), anyList()))
                .thenReturn(new LlmResponse(null, "tool_calls", List.of(
                        toolCall("call_1", "get_schema", "{}")
                )))
                .thenReturn(new LlmResponse(null, "tool_calls", List.of(
                        toolCall("call_2", "execute_sql", "{\"sql\":\"DROP TABLE USERS\"}")
                )))
                .thenReturn(new LlmResponse(null, "tool_calls", List.of(
                        toolCall("call_3", "execute_sql", "{\"sql\":\"SELECT COUNT(*) FROM USERS\"}")
                )))
                .thenReturn(new LlmResponse("共有 3 个用户。", "stop", null));

        ToolCallingResult result = agent.answer("用户表有多少人？");

        assertEquals("共有 3 个用户。", result.answer());
        assertEquals(4, result.rounds());
        assertEquals(3, result.toolCalls().size());

        // 第 2 个 tool_call 的结果必须含 Error + 危险语句被拒的错误码
        String r2 = result.toolCalls().get(1).result();
        assertTrue(r2.contains("Error"),
                "execute_sql(DROP) 应返回 Error,实际:" + r2);
        assertTrue(r2.contains("SQL_DDL_FORBIDDEN") || r2.contains("SQL_DML_FORBIDDEN") || r2.contains("SQL_UNSUPPORTED"),
                "execute_sql(DROP) 应被校验器拒绝,实际:" + r2);

        // 第 3 轮 LLM 收到的 messages 必须包含第 2 轮的 tool 消息(让 LLM 看到错误)
        ArgumentCaptor<List<ChatMessage>> msgCaptor = captor();
        verify(llmClient, times(4)).chatWithTools(msgCaptor.capture(), anyList());
        List<ChatMessage> round3Input = msgCaptor.getAllValues().get(2);
        // round3 之前应至少有 5 条消息: system, user, assistant, tool(call_1), assistant, tool(call_2)
        assertTrue(round3Input.size() >= 6,
                "round3 应能看到 round1+round2 全部历史,实际:" + round3Input.size());
        assertEquals("tool", round3Input.get(5).role());
        assertEquals("call_2", round3Input.get(5).toolCallId());
        assertTrue(round3Input.get(5).content().contains("Error"));

        // DB 仍完好(H2 仍 3 行,危险语句被 validator 拒掉)
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM USERS", Integer.class);
        assertEquals(3, count.intValue());
    }

    @Test
    void should_return_error_string_for_unknown_tool_name() {
        // round1: LLM 调用一个不存在的工具
        // round2: LLM 给出最终答案
        when(llmClient.chatWithTools(anyList(), anyList()))
                .thenReturn(new LlmResponse(null, "tool_calls", List.of(
                        toolCall("call_x", "non_existent_tool", "{}")
                )))
                .thenReturn(new LlmResponse("我没法做这件事。", "stop", null));

        ToolCallingResult result = agent.answer("做某事");

        assertEquals("我没法做这件事。", result.answer());
        assertEquals(2, result.rounds());
        String r = result.toolCalls().get(0).result();
        assertTrue(r.contains("Error"), "未知工具应返回 Error,实际:" + r);
        assertTrue(r.contains("UnknownTool"), "未知工具应标注 UnknownTool,实际:" + r);
        assertTrue(r.contains("non_existent_tool"), "未知工具应回显工具名,实际:" + r);
    }

    @Test
    void should_reject_dangerous_sql_via_validator_inside_execute_sql() {
        // round1: get_schema
        // round2: execute_sql(DROP)
        // round3: final answer
        when(llmClient.chatWithTools(anyList(), anyList()))
                .thenReturn(new LlmResponse(null, "tool_calls", List.of(
                        toolCall("call_1", "get_schema", "{}")
                )))
                .thenReturn(new LlmResponse(null, "tool_calls", List.of(
                        toolCall("call_2", "execute_sql", "{\"sql\":\"DELETE FROM USERS\"}")
                )))
                .thenReturn(new LlmResponse("无法删除。", "stop", null));

        ToolCallingResult result = agent.answer("删除所有用户");

        assertEquals(3, result.rounds());
        String r = result.toolCalls().get(1).result();
        assertTrue(r.contains("Error"), "DELETE 应被拒绝,实际:" + r);
        assertTrue(r.contains("SQL_DML_FORBIDDEN"),
                "DELETE 应返回 SQL_DML_FORBIDDEN,实际:" + r);

        // 数据未受影响
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM USERS", Integer.class);
        assertEquals(3, count.intValue());
    }

    @Test
    void should_handle_malformed_arguments_json() {
        // round1: execute_sql 参数是无效 JSON
        // round2: LLM 看到错误后给最终答案
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
    }

    // ==================== Loop control ====================

    @Test
    void should_throw_when_exceeding_max_rounds() {
        // 5 轮全部返回 tool_calls,LLM 永远不收手
        when(llmClient.chatWithTools(anyList(), anyList()))
                .thenReturn(new LlmResponse(null, "tool_calls", List.of(
                        toolCall("call_loop", "get_schema", "{}")
                )));

        ToolCallingExhaustedException ex = assertThrows(
                ToolCallingExhaustedException.class,
                () -> agent.answer("查"));

        assertTrue(ex.getMessage().contains("5"),
                "异常 message 应含轮次,实际:" + ex.getMessage());
        // 全部 5 轮 LLM 调用都被记录
        verify(llmClient, times(Nl2SqlToolAgent.MAX_ROUNDS)).chatWithTools(anyList(), anyList());
        // 5 轮 × 1 tool_call = 5 条 toolCallRecord
        assertEquals(5, ex.getToolCalls().size());
        // messages 至少含 system + user + 5 轮 (assistant + tool)
        assertEquals(2 + Nl2SqlToolAgent.MAX_ROUNDS * 2, ex.getMessages().size());
    }

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

    // ==================== Result helpers ====================

    @Test
    void should_record_full_tool_call_records_in_result() {
        when(llmClient.chatWithTools(anyList(), anyList()))
                .thenReturn(new LlmResponse(null, "tool_calls", List.of(
                        toolCall("id-A", "get_schema", "{}")
                )))
                .thenReturn(new LlmResponse(null, "tool_calls", List.of(
                        toolCall("id-B", "execute_sql", "{\"sql\":\"SELECT * FROM USERS\"}")
                )))
                .thenReturn(new LlmResponse("done", "stop", null));

        ToolCallingResult result = agent.answer("list");

        // 验证 toolCallRecord 含完整字段
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

    // ==================== Trace 验证(V1 已有断言零变更) ====================

    @Test
    void should_record_complete_trace_with_correct_order() {
        // round1: get_schema -> round2: execute_sql -> round3: final answer
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

        // 步骤顺序: USER_QUESTION → LLM_REQUEST → LLM_RESPONSE → TOOL_CALL → TOOL_RESULT
        //         → LLM_REQUEST → LLM_RESPONSE → TOOL_CALL → TOOL_RESULT
        //         → LLM_REQUEST → LLM_RESPONSE → FINAL_ANSWER
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

        // 耗时步骤的 timestamp / durationMs 字段均存在(允许为 0)
        result.trace().steps().forEach(step -> {
            assertTrue(step.timestampMs() > 0, "timestampMs 应已设置,实际:" + step.timestampMs());
            assertNotNull(step.content(), "content 字段不应为 null");
            assertTrue(step.durationMs() >= 0, "durationMs 不应为负,实际:" + step.durationMs());
        });
    }

    @Test
    void should_record_error_step_when_exceeding_max_rounds() {
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
        // 最后一步必须是 ERROR
        List<TraceStep> steps = ex.getTrace().steps();
        assertFalse(steps.isEmpty());
        assertEquals(StepType.ERROR, steps.get(steps.size() - 1).stepType());
        assertTrue(steps.get(steps.size() - 1).content().contains("5"),
                "ERROR 内容应说明超过最大轮数 5,实际:" + steps.get(steps.size() - 1).content());
    }

    @Test
    void should_record_short_tool_result_path_when_llm_answers_directly() {
        // LLM 不调用工具直接给答案:trace 中不应出现 TOOL_CALL / TOOL_RESULT
        when(llmClient.chatWithTools(anyList(), anyList()))
                .thenReturn(new LlmResponse("数据库是空的。", "stop", null));

        ToolCallingResult result = agent.answer("数据库有什么？");

        List<StepType> types = result.trace().steps().stream()
                .map(TraceStep::stepType)
                .toList();
        // 期望: USER_QUESTION → LLM_REQUEST → LLM_RESPONSE → FINAL_ANSWER
        assertEquals(
                List.of(StepType.USER_QUESTION, StepType.LLM_REQUEST,
                        StepType.LLM_RESPONSE, StepType.FINAL_ANSWER),
                types
        );
    }

    // ==================== helpers ====================

    private static ToolCall toolCall(String id, String name, String arguments) {
        return new ToolCall(id, "function", new ToolCall.Function(name, arguments));
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<List<ChatMessage>> captor() {
        return ArgumentCaptor.forClass(List.class);
    }
}
