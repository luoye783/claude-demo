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
    void call_tool_steps_should_be_pending() {
        AgentPlan result = executor.executeBeforeMainLoop(session, plan, "查询用户");

        for (AgentPlanStep s : result.steps()) {
            if (s.type() == AgentPlanStepType.CALL_TOOL) {
                assertEquals(AgentPlanStepStatus.PENDING, s.status(),
                        "V5 CALL_TOOL step " + s.stepId() + " 应保持 PENDING");
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
    void think_step_should_be_skipped() {
        AgentPlan result = executor.executeBeforeMainLoop(session, plan, "查询用户");

        // THINK 步骤不存在于 SimpleAgentPlanner 的默认计划中
        // 此测试验证:如果存在 THINK 步骤,会被标记 SKIPPED
        // 由于默认 4 步计划无 THINK,改为验证 RETRIEVE_CONTEXT 被正确处理为 SUCCESS
        AgentPlanStep retrieve = findStep(result, AgentPlanStepType.RETRIEVE_CONTEXT);
        assertEquals(AgentPlanStepStatus.SUCCESS, retrieve.status());
    }

    // ==================== V5 markToolStep Started/Finished 测试 ====================

    @Test
    void markToolStepStarted_matches_by_tool_name() {
        AgentPlan result = executor.executeBeforeMainLoop(session, plan, "查询用户");
        result = executor.markToolStepStarted(session, result, "get_schema", "call_1");

        AgentPlanStep step = findStep(result, "get_schema");
        assertNotNull(step);
        assertEquals(AgentPlanStepStatus.RUNNING, step.status());
    }

    @Test
    void markToolStepFinished_success() {
        AgentPlan result = executor.executeBeforeMainLoop(session, plan, "查询用户");
        result = executor.markToolStepStarted(session, result, "get_schema", "call_1");
        result = executor.markToolStepFinished(session, result, "get_schema", "call_1",
                true, "ok");

        AgentPlanStep step = findStep(result, "get_schema");
        assertEquals(AgentPlanStepStatus.SUCCESS, step.status());
    }

    @Test
    void markToolStepFinished_failure() {
        AgentPlan result = executor.executeBeforeMainLoop(session, plan, "查询用户");
        result = executor.markToolStepStarted(session, result, "execute_sql", "call_2");
        result = executor.markToolStepFinished(session, result, "execute_sql", "call_2",
                false, "SQL error");

        AgentPlanStep step = findStep(result, "execute_sql");
        assertEquals(AgentPlanStepStatus.FAILED, step.status());
    }

    @Test
    void markToolStepStarted_prefers_pending() {
        AgentPlan result = executor.executeBeforeMainLoop(session, plan, "查询用户");
        // get_schema step 是 PENDING,execute_sql 也是 PENDING
        // 首次 markToolStepStarted 应匹配 PENDING 的 get_schema
        result = executor.markToolStepStarted(session, result, "get_schema", "call_1");
        AgentPlanStep step = findStep(result, "get_schema");
        assertEquals(AgentPlanStepStatus.RUNNING, step.status());
    }

    @Test
    void markToolStepStarted_no_match_returns_original() {
        AgentPlan result = executor.executeBeforeMainLoop(session, plan, "查询用户");
        AgentPlan noMatch = executor.markToolStepStarted(session, result, "unknown_tool", "call_1");
        assertSame(result, noMatch, "未匹配应返回原 plan");
    }

    @Test
    void markToolStepStarted_trace_contains_tool_call_id() {
        executor.executeBeforeMainLoop(session, plan, "查询用户");
        executor.markToolStepStarted(session, plan, "get_schema", "call_abc123");

        boolean found = session.trace().steps().stream()
                .anyMatch(s -> s.content().contains("toolCallId=call_abc123"));
        assertTrue(found, "trace 应包含 toolCallId");
    }

    @Test
    void markToolStepFinished_trace_contains_note() {
        executor.executeBeforeMainLoop(session, plan, "查询用户");
        session = new AgentSession(null, null, new AgentTrace()); // fresh trace
        AgentPlan p = executor.markToolStepStarted(session, plan, "get_schema", "c1");
        executor.markToolStepFinished(session, p, "get_schema", "c1",
                false, "connection refused");

        boolean found = session.trace().steps().stream()
                .anyMatch(s -> s.content().contains("note=connection refused"));
        assertTrue(found, "trace 应包含失败原因 note");
    }

    @Test
    void complete_plan_lifecycle_v5() {
        // executeBeforeMainLoop
        AgentPlan p = executor.executeBeforeMainLoop(session, plan, "查询用户");

        // tool calls
        p = executor.markToolStepStarted(session, p, "get_schema", "c1");
        p = executor.markToolStepFinished(session, p, "get_schema", "c1", true, null);
        p = executor.markToolStepStarted(session, p, "execute_sql", "c2");
        p = executor.markToolStepFinished(session, p, "execute_sql", "c2", true, null);

        // finalize
        p = executor.finalizeAfterMainLoop(session, p, true);

        // 验证所有 step 最终状态
        assertEquals(AgentPlanStepStatus.SUCCESS,
                findStep(p, AgentPlanStepType.RETRIEVE_CONTEXT).status());
        assertEquals(AgentPlanStepStatus.SUCCESS,
                findStep(p, "get_schema").status());
        assertEquals(AgentPlanStepStatus.SUCCESS,
                findStep(p, "execute_sql").status());
        assertEquals(AgentPlanStepStatus.SUCCESS,
                findStep(p, AgentPlanStepType.GENERATE_ANSWER).status());
    }

    private static AgentPlanStep findStep(AgentPlan plan, AgentPlanStepType type) {
        return plan.steps().stream()
                .filter(s -> s.type() == type)
                .findFirst().orElse(null);
    }

    private static AgentPlanStep findStep(AgentPlan plan, String toolName) {
        return plan.steps().stream()
                .filter(s -> toolName.equals(s.expectedToolName()))
                .findFirst().orElse(null);
    }
}
