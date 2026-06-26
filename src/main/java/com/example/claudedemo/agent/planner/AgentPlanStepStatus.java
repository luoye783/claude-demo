package com.example.claudedemo.agent.planner;

/**
 * Planner 步骤执行状态(Agent Runtime V3).
 *
 * @since 0.0.1
 */
public enum AgentPlanStepStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
    SKIPPED
}
