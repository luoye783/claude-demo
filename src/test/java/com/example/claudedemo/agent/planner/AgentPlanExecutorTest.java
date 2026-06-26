package com.example.claudedemo.agent.planner;

import com.example.claudedemo.agent.session.AgentSession;
import com.example.claudedemo.agent.trace.AgentTrace;
import com.example.claudedemo.agent.trace.StepType;
import com.example.claudedemo.agent.trace.TraceStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AgentPlanExecutor} 单元测试.
 *
 * @since 0.0.1
 */
class AgentPlanExecutorTest {

    private AgentPlanExecutor executor;
    private AgentSession session;
    private AgentPlan plan;

    @BeforeEach
    void setUp() {
        executor = new AgentPlanExecutor();
        session = new AgentSession(null, null, new AgentTrace());

        SimpleAgentPlanner planner = new SimpleAgentPlanner();
        plan = planner.plan(session, "查询用户");
    }

    @Test
    void retrieve_context_step_should_be_success() {
        AgentPlan result = executor.executeBeforeMainLoop(session, plan, "查询用户");

        AgentPlanStep step = findStep(result, AgentPlanStepType.RETRIEVE_CONTEXT);
        assertNotNull(step);
        assertEquals(AgentPlanStepStatus.SUCCESS, step.status());
    }

    @Test
    void call_tool_steps_should_be_skipped() {
        AgentPlan result = executor.executeBeforeMainLoop(session, plan, "查询用户");

        for (AgentPlanStep s : result.steps()) {
            if (s.type() == AgentPlanStepType.CALL_TOOL) {
                assertEquals(AgentPlanStepStatus.SKIPPED, s.status(),
                        "CALL_TOOL step " + s.stepId() + " 应为 SKIPPED");
            }
        }
    }

    @Test
    void generate_answer_step_should_stay_pending_before_finalize() {
        AgentPlan result = executor.executeBeforeMainLoop(session, plan, "查询用户");

        AgentPlanStep step = findStep(result, AgentPlanStepType.GENERATE_ANSWER);
        assertNotNull(step);
        assertEquals(AgentPlanStepStatus.PENDING, step.status(),
                "finalize 前 GENERATE_ANSWER 应为 PENDING");
    }

    @Test
    void finalize_success_marks_answer_success() {
        AgentPlan afterLoop = executor.executeBeforeMainLoop(session, plan, "查询用户");
        AgentPlan finalized = executor.finalizeAfterMainLoop(session, afterLoop, true);

        AgentPlanStep step = findStep(finalized, AgentPlanStepType.GENERATE_ANSWER);
        assertEquals(AgentPlanStepStatus.SUCCESS, step.status());
    }

    @Test
    void finalize_failure_marks_answer_failed() {
        AgentPlan afterLoop = executor.executeBeforeMainLoop(session, plan, "查询用户");
        AgentPlan finalized = executor.finalizeAfterMainLoop(session, afterLoop, false);

        AgentPlanStep step = findStep(finalized, AgentPlanStepType.GENERATE_ANSWER);
        assertEquals(AgentPlanStepStatus.FAILED, step.status());
    }

    @Test
    void trace_contains_plan_step_events() {
        executor.executeBeforeMainLoop(session, plan, "查询用户");

        boolean hasStarted = false;
        boolean hasFinished = false;
        for (TraceStep s : session.trace().steps()) {
            if (s.stepType() == StepType.PLAN_STEP_STARTED) hasStarted = true;
            if (s.stepType() == StepType.PLAN_STEP_FINISHED) hasFinished = true;
        }
        assertTrue(hasStarted, "trace 应包含 PLAN_STEP_STARTED");
        assertTrue(hasFinished, "trace 应包含 PLAN_STEP_FINISHED");
    }

    @Test
    void trace_content_includes_step_info() {
        executor.executeBeforeMainLoop(session, plan, "查询用户");

        TraceStep finished = session.trace().steps().stream()
                .filter(s -> s.stepType() == StepType.PLAN_STEP_FINISHED)
                .findFirst().orElse(null);
        assertNotNull(finished);
        assertTrue(finished.content().contains("stepId="));
        assertTrue(finished.content().contains("type="));
        assertTrue(finished.content().contains("status="));
    }

    @Test
    void original_plan_is_unchanged() {
        // 保存原始状态
        AgentPlanStep originalStep1 = plan.steps().get(0);

        executor.executeBeforeMainLoop(session, plan, "查询用户");

        // 传入的 plan 对象不受影响
        assertEquals(AgentPlanStepStatus.PENDING, plan.steps().get(0).status());
        assertEquals(AgentPlanStepStatus.PENDING, plan.steps().get(1).status());
    }

    @Test
    void skipped_steps_have_note_in_trace() {
        executor.executeBeforeMainLoop(session, plan, "查询用户");

        boolean hasSkipNote = session.trace().steps().stream()
                .filter(s -> s.stepType() == StepType.PLAN_STEP_FINISHED
                        && s.content().contains("status=SKIPPED"))
                .anyMatch(s -> s.content().contains("tool loop"));
        assertTrue(hasSkipNote, "SKIPPED trace 应说明原因");
    }

    private static AgentPlanStep findStep(AgentPlan plan, AgentPlanStepType type) {
        return plan.steps().stream()
                .filter(s -> s.type() == type)
                .findFirst().orElse(null);
    }
}
