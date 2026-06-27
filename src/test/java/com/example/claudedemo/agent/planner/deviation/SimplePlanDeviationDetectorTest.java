package com.example.claudedemo.agent.planner.deviation;

import com.example.claudedemo.agent.planner.AgentPlan;
import com.example.claudedemo.agent.planner.AgentPlanStep;
import com.example.claudedemo.agent.planner.AgentPlanStepStatus;
import com.example.claudedemo.agent.planner.AgentPlanStepType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SimplePlanDeviationDetector} 单元测试.
 *
 * @since 0.0.1
 */
class SimplePlanDeviationDetectorTest {

    private SimplePlanDeviationDetector detector;

    @BeforeEach
    void setUp() {
        detector = new SimplePlanDeviationDetector();
    }

    private static AgentPlan plan(String... toolNames) {
        List<AgentPlanStep> steps = new ArrayList<>();
        for (int i = 0; i < toolNames.length; i++) {
            steps.add(new AgentPlanStep("step-" + (i + 1), i + 1,
                    AgentPlanStepType.CALL_TOOL, "desc",
                    toolNames[i], AgentPlanStepStatus.PENDING));
        }
        // 补一个 GENERATE_ANSWER
        steps.add(new AgentPlanStep("step-" + (steps.size() + 1), steps.size() + 1,
                AgentPlanStepType.GENERATE_ANSWER, "answer",
                null, AgentPlanStepStatus.PENDING));
        return new AgentPlan("test-q", steps);
    }

    private static ToolExecutionRecord successExec(String toolName, int order) {
        return new ToolExecutionRecord("c-" + order, toolName, order, true,
                0, 1, null);
    }

    private static ToolExecutionRecord failedExec(String toolName, int order, String error) {
        return new ToolExecutionRecord("c-" + order, toolName, order, false,
                0, 1, error);
    }

    @Test
    void perfect_execution_no_deviations() {
        AgentPlan p = plan("get_schema", "execute_sql");
        // 模拟 markToolStepFinished 已将 step 状态更新为 SUCCESS
        p = p.updateStepStatus("step-1", AgentPlanStepStatus.SUCCESS);
        p = p.updateStepStatus("step-2", AgentPlanStepStatus.SUCCESS);
        List<ToolExecutionRecord> execs = List.of(
                successExec("get_schema", 1),
                successExec("execute_sql", 2));
        List<PlanDeviation> deviations = detector.detect(p, execs);
        assertTrue(deviations.isEmpty(), "完美执行应无偏差: " + deviations);
    }

    @Test
    void missing_tool_call() {
        AgentPlan p = plan("get_schema", "execute_sql");
        List<ToolExecutionRecord> execs = List.of(
                successExec("get_schema", 1));
        List<PlanDeviation> deviations = detector.detect(p, execs);
        assertFalse(deviations.isEmpty());
        assertTrue(hasDeviation(deviations, PlanDeviationType.MISSING_TOOL_CALL));
    }

    @Test
    void unplanned_tool_call() {
        AgentPlan p = plan("get_schema");
        List<ToolExecutionRecord> execs = List.of(
                successExec("get_schema", 1),
                successExec("some_other_tool", 2));
        List<PlanDeviation> deviations = detector.detect(p, execs);
        assertTrue(hasDeviation(deviations, PlanDeviationType.UNPLANNED_TOOL_CALL));
    }

    @Test
    void tool_step_failed() {
        AgentPlan p = plan("execute_sql");
        List<ToolExecutionRecord> execs = List.of(
                failedExec("execute_sql", 1, "connection refused"));
        List<PlanDeviation> deviations = detector.detect(p, execs);
        assertTrue(hasDeviation(deviations, PlanDeviationType.TOOL_STEP_FAILED));
    }

    @Test
    void pending_required_step_only_for_executed_but_still_pending() {
        AgentPlan p = plan("get_schema");
        // get_schema 调用了但 step 仍 PENDING(simulates markToolStepFinished 未匹配)
        List<ToolExecutionRecord> execs = List.of(
                successExec("get_schema", 1));
        List<PlanDeviation> deviations = detector.detect(p, execs);
        // 工具实际执行了但 step 仍是 PENDING → PENDING_REQUIRED_STEP
        assertTrue(hasDeviation(deviations, PlanDeviationType.PENDING_REQUIRED_STEP));
    }

    @Test
    void missing_tool_not_in_executed_no_pending_required_step() {
        AgentPlan p = plan("get_schema", "execute_sql");
        // 只调了 get_schema, execute_sql 没调 → MISSING, 不报 PENDING
        List<ToolExecutionRecord> execs = List.of(
                successExec("get_schema", 1));
        List<PlanDeviation> deviations = detector.detect(p, execs);
        assertTrue(hasDeviation(deviations, PlanDeviationType.MISSING_TOOL_CALL));
        boolean hasPendingForMissing = deviations.stream()
                .anyMatch(d -> d.type() == PlanDeviationType.PENDING_REQUIRED_STEP
                        && d.message().contains("execute_sql"));
        assertFalse(hasPendingForMissing, "未调用的工具不应报 PENDING_REQUIRED_STEP");
    }

    @Test
    void tool_order_mismatch() {
        AgentPlan p = plan("get_schema", "execute_sql");
        List<ToolExecutionRecord> execs = List.of(
                successExec("execute_sql", 1),
                successExec("get_schema", 2));
        List<PlanDeviation> deviations = detector.detect(p, execs);
        assertTrue(hasDeviation(deviations, PlanDeviationType.TOOL_ORDER_MISMATCH));
    }

    @Test
    void order_match_ignores_extra_tools() {
        AgentPlan p = plan("get_schema", "execute_sql");
        // 计划外工具在中间不应影响顺序判断
        List<ToolExecutionRecord> execs = List.of(
                successExec("get_schema", 1),
                successExec("other_tool", 2),
                successExec("execute_sql", 3));
        List<PlanDeviation> deviations = detector.detect(p, execs);
        assertFalse(hasDeviation(deviations, PlanDeviationType.TOOL_ORDER_MISMATCH),
                "计划外工具不应影响顺序判断");
    }

    @Test
    void failed_tool_ignored_by_order_check() {
        AgentPlan p = plan("get_schema", "execute_sql");
        List<ToolExecutionRecord> execs = List.of(
                successExec("get_schema", 1),
                failedExec("execute_sql", 2, "error"));
        List<PlanDeviation> deviations = detector.detect(p, execs);
        assertTrue(hasDeviation(deviations, PlanDeviationType.TOOL_STEP_FAILED));
        assertFalse(hasDeviation(deviations, PlanDeviationType.TOOL_ORDER_MISMATCH),
                "失败工具不应参与顺序检测");
    }

    @Test
    void empty_executions_all_missing() {
        AgentPlan p = plan("get_schema", "execute_sql");
        List<PlanDeviation> deviations = detector.detect(p, List.of());
        assertEquals(2, deviations.stream()
                .filter(d -> d.type() == PlanDeviationType.MISSING_TOOL_CALL).count());
    }

    @Test
    void null_plan_returns_empty() {
        List<PlanDeviation> deviations = detector.detect(null, List.of());
        assertTrue(deviations.isEmpty());
    }

    @Test
    void null_executions_treated_as_empty() {
        AgentPlan p = plan("get_schema");
        List<PlanDeviation> deviations = detector.detect(p, null);
        assertTrue(hasDeviation(deviations, PlanDeviationType.MISSING_TOOL_CALL));
    }

    private static boolean hasDeviation(List<PlanDeviation> deviations, PlanDeviationType type) {
        return deviations.stream().anyMatch(d -> d.type() == type);
    }
}
