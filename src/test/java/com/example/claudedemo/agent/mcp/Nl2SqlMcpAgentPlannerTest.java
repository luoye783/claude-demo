package com.example.claudedemo.agent.mcp;

import com.example.claudedemo.agent.ToolCallingResult;
import com.example.claudedemo.agent.memory.InMemoryConversationStore;
import com.example.claudedemo.agent.planner.AgentPlan;
import com.example.claudedemo.agent.planner.PlannerProperties;
import com.example.claudedemo.agent.planner.SimpleAgentPlanner;
import com.example.claudedemo.agent.trace.StepType;
import com.example.claudedemo.agent.trace.TraceStep;
import com.example.claudedemo.llm.LlmClient;
import com.example.claudedemo.llm.LlmResponse;
import com.example.claudedemo.llm.ToolCall;
import com.example.claudedemo.llm.ToolDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link Nl2SqlMcpAgent} + {@link com.example.claudedemo.agent.planner.SimpleAgentPlanner} 集成测试.
 *
 * <p>验证 Planner 接入后不影响原有 tool calling 流程。
 *
 * @since 0.0.1
 */
class Nl2SqlMcpAgentPlannerTest {

    private static final List<ToolDefinition> FAKE_TOOL_DEFS = List.of(
            new ToolDefinition("get_schema", "获取数据库所有表的结构",
                    Map.of("type", "object", "properties", Map.of(), "required", List.of())),
            new ToolDefinition("execute_sql", "执行只读 SELECT SQL",
                    Map.of("type", "object", "properties", Map.of(
                            "sql", Map.of("type", "string")
                    ), "required", List.of("sql")))
    );

    private static ToolCall toolCall(String id, String name, String args) {
        return new ToolCall(id, "function", new ToolCall.Function(name, args));
    }

    private Nl2SqlMcpAgent agentWithPlanner(boolean enabled) {
        LlmClient llmClient = mock(LlmClient.class);
        McpToolClient mcpToolClient = mock(McpToolClient.class);
        when(mcpToolClient.listTools()).thenReturn(FAKE_TOOL_DEFS);
        when(mcpToolClient.callTool(eq("get_schema"), anyString())).thenReturn("表 USERS(id, name)");
        when(mcpToolClient.callTool(eq("execute_sql"), anyString())).thenReturn("{\"rows\":[1]}");
        when(llmClient.chatWithTools(anyList(), anyList()))
                .thenReturn(new LlmResponse(null, "tool_calls", List.of(
                        toolCall("c1", "get_schema", "{}")
                )))
                .thenReturn(new LlmResponse("查询完成", "stop", null));

        PlannerProperties props = new PlannerProperties();
        props.setEnabled(enabled);

        return new Nl2SqlMcpAgent(llmClient, mcpToolClient,
                new InMemoryConversationStore(), null, null,
                new SimpleAgentPlanner(), props);
    }

    @Test
    void planner_enabled_should_create_plan() {
        Nl2SqlMcpAgent agent = agentWithPlanner(true);
        ToolCallingResult result = agent.answer("查询用户");

        // trace 应包含 PLAN_CREATED
        boolean hasPlanCreated = result.trace().steps().stream()
                .anyMatch(s -> s.stepType() == StepType.PLAN_CREATED);
        assertTrue(hasPlanCreated, "trace 应包含 PLAN_CREATED 步骤");
    }

    @Test
    void planner_disabled_should_not_create_plan() {
        Nl2SqlMcpAgent agent = agentWithPlanner(false);
        ToolCallingResult result = agent.answer("查询用户");

        boolean hasPlanCreated = result.trace().steps().stream()
                .anyMatch(s -> s.stepType() == StepType.PLAN_CREATED);
        assertFalse(hasPlanCreated, "planner 关闭时 trace 不应包含 PLAN_CREATED");
    }

    @Test
    void planner_null_should_not_create_plan() {
        // V1 构造器不传 planner → null
        LlmClient llmClient = mock(LlmClient.class);
        McpToolClient mcpToolClient = mock(McpToolClient.class);
        when(mcpToolClient.listTools()).thenReturn(FAKE_TOOL_DEFS);
        when(mcpToolClient.callTool(anyString(), anyString())).thenReturn("ok");
        when(llmClient.chatWithTools(anyList(), anyList()))
                .thenReturn(new LlmResponse("完成", "stop", null));

        Nl2SqlMcpAgent agent = new Nl2SqlMcpAgent(llmClient, mcpToolClient);
        ToolCallingResult result = agent.answer("查询用户");

        boolean hasPlanCreated = result.trace().steps().stream()
                .anyMatch(s -> s.stepType() == StepType.PLAN_CREATED);
        assertFalse(hasPlanCreated, "V1 构造器(planner=null)不应创建计划");
    }

    @Test
    void planner_enabled_session_metadata_contains_plan() {
        Nl2SqlMcpAgent agent = agentWithPlanner(true);
        ToolCallingResult result = agent.answer("s1", "查询用户");

        // PLAN_CREATED trace step content 应包含计划摘要
        TraceStep planStep = result.trace().steps().stream()
                .filter(s -> s.stepType() == StepType.PLAN_CREATED)
                .findFirst().orElse(null);
        assertNotNull(planStep);
        assertTrue(planStep.content().contains("steps"));
        assertTrue(planStep.content().contains("get_schema"));
        assertTrue(planStep.content().contains("execute_sql"));
    }

    @Test
    void planner_does_not_affect_tool_calling() {
        Nl2SqlMcpAgent agent = agentWithPlanner(true);
        ToolCallingResult result = agent.answer("查询用户");

        assertNotNull(result.answer());
        assertFalse(result.answer().isBlank());
        // 原有 tool call 记录应完整
        assertFalse(result.toolCalls().isEmpty());
        assertTrue(result.rounds() > 0);
    }

    @Test
    void trace_step_order_is_correct() {
        Nl2SqlMcpAgent agent = agentWithPlanner(true);
        ToolCallingResult result = agent.answer("查询用户");

        List<TraceStep> steps = result.trace().steps();
        // USER_QUESTION → PLAN_CREATED → RAG_RETRIEVE(可能) → LLM_REQUEST → ...
        int userIdx = indexOf(steps, StepType.USER_QUESTION);
        int planIdx = indexOf(steps, StepType.PLAN_CREATED);
        assertTrue(userIdx >= 0 && planIdx >= 0);
        assertTrue(userIdx < planIdx, "PLAN_CREATED 应在 USER_QUESTION 之后");
    }

    @Test
    void v2_constructor_no_planner_should_work() {
        LlmClient llmClient = mock(LlmClient.class);
        McpToolClient mcpToolClient = mock(McpToolClient.class);
        when(mcpToolClient.listTools()).thenReturn(FAKE_TOOL_DEFS);
        when(mcpToolClient.callTool(anyString(), anyString())).thenReturn("ok");
        when(llmClient.chatWithTools(anyList(), anyList()))
                .thenReturn(new LlmResponse("完成", "stop", null));

        Nl2SqlMcpAgent agent = new Nl2SqlMcpAgent(llmClient, mcpToolClient,
                new InMemoryConversationStore());
        ToolCallingResult result = agent.answer("查询用户");

        assertNotNull(result.answer());
        assertFalse(result.trace().steps().isEmpty());
    }

    // ==================== V4 PlanExecutor 测试 ====================

    @Test
    void v4_trace_contains_plan_step_events() {
        Nl2SqlMcpAgent agent = agentWithPlanner(true);
        ToolCallingResult result = agent.answer("查询用户");

        boolean hasPlanStarted = result.trace().steps().stream()
                .anyMatch(s -> s.stepType() == StepType.PLAN_STEP_STARTED);
        boolean hasPlanFinished = result.trace().steps().stream()
                .anyMatch(s -> s.stepType() == StepType.PLAN_STEP_FINISHED);

        assertTrue(hasPlanStarted, "V4 trace 应包含 PLAN_STEP_STARTED");
        assertTrue(hasPlanFinished, "V4 trace 应包含 PLAN_STEP_FINISHED");
    }

    @Test
    void v4_trace_contains_plan_created_and_step_events() {
        Nl2SqlMcpAgent agent = agentWithPlanner(true);
        ToolCallingResult result = agent.answer("查询用户");

        List<TraceStep> steps = result.trace().steps();
        int createdIdx = indexOf(steps, StepType.PLAN_CREATED);
        int startedIdx = indexOf(steps, StepType.PLAN_STEP_STARTED);
        int finishedIdx = indexOf(steps, StepType.PLAN_STEP_FINISHED);

        assertTrue(createdIdx >= 0);
        assertTrue(startedIdx >= 0);
        assertTrue(finishedIdx >= 0);
        // PLAN_CREATED 应在步骤事件之前
        assertTrue(createdIdx < startedIdx, "PLAN_CREATED 应在 PLAN_STEP_STARTED 之前");
    }

    @Test
    void v4_step_status_not_all_pending() {
        Nl2SqlMcpAgent agent = agentWithPlanner(true);
        ToolCallingResult result = agent.answer("s2", "查询用户");

        // trace 中 PLAN_STEP_FINISHED 的 status 不应全是 PENDING
        boolean hasNonPending = result.trace().steps().stream()
                .filter(s -> s.stepType() == StepType.PLAN_STEP_FINISHED)
                .anyMatch(s -> s.content().contains("status=SUCCESS")
                        || s.content().contains("status=SKIPPED"));
        assertTrue(hasNonPending, "完成步骤的状态不应全是 PENDING");
    }

    @Test
    void v4_planner_disabled_no_plan_events() {
        Nl2SqlMcpAgent agent = agentWithPlanner(false);
        ToolCallingResult result = agent.answer("查询用户");

        boolean hasAnyPlanEvent = result.trace().steps().stream()
                .anyMatch(s -> s.stepType() == StepType.PLAN_CREATED
                        || s.stepType() == StepType.PLAN_STEP_STARTED
                        || s.stepType() == StepType.PLAN_STEP_FINISHED);
        assertFalse(hasAnyPlanEvent, "planner disabled 时不应有任何 PLAN_* trace");
    }

    // ==================== V5 Tool Step Executor 测试 ====================

    @Test
    void v5_tool_call_trace_contains_tool_call_id() {
        Nl2SqlMcpAgent agent = agentWithPlanner(true);
        ToolCallingResult result = agent.answer("查询用户");

        boolean hasToolCallId = result.trace().steps().stream()
                .anyMatch(s -> s.content().contains("toolCallId="));
        assertTrue(hasToolCallId, "V5 trace 应包含 toolCallId");
    }

    @Test
    void v5_plan_step_started_before_tool_call() {
        Nl2SqlMcpAgent agent = agentWithPlanner(true);
        ToolCallingResult result = agent.answer("查询用户");

        List<TraceStep> steps = result.trace().steps();
        int planStarted = indexOf(steps, StepType.PLAN_STEP_STARTED);
        int toolCall = lastIndexOf(steps, StepType.TOOL_CALL);

        assertTrue(planStarted >= 0 && toolCall >= 0);
        assertTrue(planStarted < toolCall,
                "PLAN_STEP_STARTED 应在 TOOL_CALL 之前");
    }

    @Test
    void v5_tool_call_success_marks_plan_step_success() {
        Nl2SqlMcpAgent agent = agentWithPlanner(true);
        ToolCallingResult result = agent.answer("s3", "查询用户");

        // trace 中应有 get_schema 的 PLAN_STEP_FINISHED status=SUCCESS
        boolean hasGetSchemaSuccess = result.trace().steps().stream()
                .filter(s -> s.stepType() == StepType.PLAN_STEP_FINISHED)
                .anyMatch(s -> s.content().contains("tool=get_schema")
                        && s.content().contains("status=SUCCESS"));
        assertTrue(hasGetSchemaSuccess, "真实 get_schema 调用后应标记 SUCCESS");
    }

    private int indexOf(List<TraceStep> steps, StepType type) {
        for (int i = 0; i < steps.size(); i++) {
            if (steps.get(i).stepType() == type) return i;
        }
        return -1;
    }

    private int lastIndexOf(List<TraceStep> steps, StepType type) {
        for (int i = steps.size() - 1; i >= 0; i--) {
            if (steps.get(i).stepType() == type) return i;
        }
        return -1;
    }
}
