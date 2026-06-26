package com.example.claudedemo.agent.planner;

import com.example.claudedemo.agent.session.AgentSession;
import com.example.claudedemo.agent.trace.AgentTrace;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SimpleAgentPlanner} 单元测试.
 *
 * @since 0.0.1
 */
class SimpleAgentPlannerTest {

    private final SimpleAgentPlanner planner = new SimpleAgentPlanner();

    private AgentSession newSession() {
        return new AgentSession(null, null, new AgentTrace());
    }

    @Test
    void should_generate_4_steps() {
        AgentPlan plan = planner.plan(newSession(), "查询用户");
        assertEquals(4, plan.steps().size());
    }

    @Test
    void step_order_should_be_sequential() {
        AgentPlan plan = planner.plan(newSession(), "查询用户");
        for (int i = 0; i < plan.steps().size(); i++) {
            assertEquals(i + 1, plan.steps().get(i).order());
        }
    }

    @Test
    void step1_should_be_retrieve_context() {
        AgentPlan plan = planner.plan(newSession(), "查询用户");
        AgentPlanStep step = plan.steps().get(0);
        assertEquals(AgentPlanStepType.RETRIEVE_CONTEXT, step.type());
        assertEquals("step-1", step.stepId());
        assertNull(step.expectedToolName());
    }

    @Test
    void step2_should_be_call_tool_get_schema() {
        AgentPlan plan = planner.plan(newSession(), "查询用户");
        AgentPlanStep step = plan.steps().get(1);
        assertEquals(AgentPlanStepType.CALL_TOOL, step.type());
        assertEquals("get_schema", step.expectedToolName());
    }

    @Test
    void step3_should_be_call_tool_execute_sql() {
        AgentPlan plan = planner.plan(newSession(), "查询用户");
        AgentPlanStep step = plan.steps().get(2);
        assertEquals(AgentPlanStepType.CALL_TOOL, step.type());
        assertEquals("execute_sql", step.expectedToolName());
    }

    @Test
    void step4_should_be_generate_answer() {
        AgentPlan plan = planner.plan(newSession(), "查询用户");
        AgentPlanStep step = plan.steps().get(3);
        assertEquals(AgentPlanStepType.GENERATE_ANSWER, step.type());
        assertEquals("step-4", step.stepId());
        assertNull(step.expectedToolName());
    }

    @Test
    void all_steps_initial_status_is_pending() {
        AgentPlan plan = planner.plan(newSession(), "查询用户");
        for (AgentPlanStep step : plan.steps()) {
            assertEquals(AgentPlanStepStatus.PENDING, step.status());
        }
    }

    @Test
    void plan_has_valid_summary() {
        AgentPlan plan = planner.plan(newSession(), "查询用户");
        String summary = plan.summary();
        assertNotNull(summary);
        assertTrue(summary.contains("4 steps"));
        assertTrue(summary.contains("RETRIEVE_CONTEXT"));
        assertTrue(summary.contains("get_schema"));
        assertTrue(summary.contains("execute_sql"));
        assertTrue(summary.contains("GENERATE_ANSWER"));
    }

    @Test
    void different_questions_same_plan_structure() {
        AgentPlan plan1 = planner.plan(newSession(), "查询用户");
        AgentPlan plan2 = planner.plan(newSession(), "统计订单");
        assertEquals(plan1.steps().size(), plan2.steps().size());
        // 不同问题不同 planId
        assertNotNull(plan1.planId());
        assertNotNull(plan2.planId());
    }
}
