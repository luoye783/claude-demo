package com.example.claudedemo.agent.planner;

/**
 * Planner 步骤类型(Agent Runtime V3).
 *
 * @since 0.0.1
 */
public enum AgentPlanStepType {
    /** 内部推理. */
    THINK,
    /** 检索或整理可用上下文(memory / summary / RAG). */
    RETRIEVE_CONTEXT,
    /** 调用工具. */
    CALL_TOOL,
    /** 生成最终答案. */
    GENERATE_ANSWER
}
