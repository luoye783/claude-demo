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

    private int indexOf(List<TraceStep> steps, StepType type) {
        for (int i = 0; i < steps.size(); i++) {
            if (steps.get(i).stepType() == type) return i;
        }
        return -1;
    }
}
