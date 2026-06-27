package com.example.claudedemo.agent.planner.deviation;

/**
 * 计划偏差类型(Agent Runtime V6).
 *
 * @since 0.0.1
 */
public enum PlanDeviationType {
    /** 计划要求调用某工具,但实际未调用. */
    MISSING_TOOL_CALL,
    /** 实际调用了计划外工具. */
    UNPLANNED_TOOL_CALL,
    /** 工具调用顺序与计划不一致. */
    TOOL_ORDER_MISMATCH,
    /** 工具调用执行失败. */
    TOOL_STEP_FAILED,
    /** 计划步骤在回答结束后仍为 PENDING. */
    PENDING_REQUIRED_STEP
}
