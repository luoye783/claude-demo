package com.example.claudedemo.agent.planner;

/**
 * Planner 单个执行步骤(Agent Runtime V4).
 *
 * @param stepId           步骤 ID,如 "step-1"
 * @param order            执行顺序(1-based)
 * @param type             步骤类型
 * @param description      人类可读描述
 * @param expectedToolName CALL_TOOL 时的目标工具名,其余为 null
 * @param status           执行状态,初始 PENDING
 * @since 0.0.1
 */
public record AgentPlanStep(
        String stepId,
        int order,
        AgentPlanStepType type,
        String description,
        String expectedToolName,
        AgentPlanStepStatus status
) {

    public AgentPlanStep {
        if (order < 1) order = 1;
        if (status == null) status = AgentPlanStepStatus.PENDING;
    }

    /**
     * 返回一个新的 AgentPlanStep,仅 status 字段不同(V4 不可变状态流转).
     */
    public AgentPlanStep withStatus(AgentPlanStepStatus newStatus) {
        return new AgentPlanStep(stepId, order, type, description,
                expectedToolName, newStatus);
    }
}
