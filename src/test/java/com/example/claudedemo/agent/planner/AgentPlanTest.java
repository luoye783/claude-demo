package com.example.claudedemo.agent.planner;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * {@link AgentPlanStep} 和 {@link AgentPlan} 不可变状态流转单元测试.
 *
 * @since 0.0.1
 */
class AgentPlanTest {

    private static AgentPlanStep makeStep(String id, AgentPlanStepType type) {
        return new AgentPlanStep(id, 1, type, "desc", null, AgentPlanStepStatus.PENDING);
    }

    private static AgentPlan makePlan(List<AgentPlanStep> steps) {
        return new AgentPlan("test-q", steps);
    }

    @Test
    void withStatus_returns_new_step() {
        AgentPlanStep step = makeStep("step-1", AgentPlanStepType.CALL_TOOL);
        AgentPlanStep updated = step.withStatus(AgentPlanStepStatus.SUCCESS);

        assertNotSame(step, updated, "应返回新对象");
        assertEquals(AgentPlanStepStatus.PENDING, step.status(), "原 step 不变");
        assertEquals(AgentPlanStepStatus.SUCCESS, updated.status());
        assertEquals(step.stepId(), updated.stepId());
        assertEquals(step.type(), updated.type());
    }

    @Test
    void updateStepStatus_returns_new_plan() {
        AgentPlanStep step1 = makeStep("step-1", AgentPlanStepType.RETRIEVE_CONTEXT);
        AgentPlanStep step2 = makeStep("step-2", AgentPlanStepType.CALL_TOOL);
        AgentPlan plan = makePlan(List.of(step1, step2));

        AgentPlan updated = plan.updateStepStatus("step-1", AgentPlanStepStatus.SUCCESS);
        assertNotSame(plan, updated, "应返回新 plan");

        // 原 plan 不变
        assertEquals(AgentPlanStepStatus.PENDING, plan.steps().get(0).status());
        assertEquals(AgentPlanStepStatus.PENDING, plan.steps().get(1).status());

        // 新 plan 中 step-1 已更新
        assertEquals(AgentPlanStepStatus.SUCCESS, updated.steps().get(0).status());
        assertEquals(AgentPlanStepStatus.PENDING, updated.steps().get(1).status());
    }

    @Test
    void updateStepStatus_unknown_id_returns_same_plan() {
        AgentPlan plan = makePlan(List.of(makeStep("step-1", AgentPlanStepType.CALL_TOOL)));
        AgentPlan result = plan.updateStepStatus("no-such-step", AgentPlanStepStatus.SUCCESS);
        assertSame(plan, result, "不存在的 stepId 应返回原 plan");
    }

    @Test
    void chained_updates_work() {
        AgentPlan plan = makePlan(List.of(makeStep("step-1", AgentPlanStepType.RETRIEVE_CONTEXT)));

        AgentPlan updated = plan
                .updateStepStatus("step-1", AgentPlanStepStatus.RUNNING)
                .updateStepStatus("step-1", AgentPlanStepStatus.SUCCESS);

        assertEquals(AgentPlanStepStatus.SUCCESS, updated.steps().get(0).status());
        assertEquals(AgentPlanStepStatus.PENDING, plan.steps().get(0).status());
    }
}
