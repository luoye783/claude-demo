package com.example.claudedemo.agent.planner;

import com.example.claudedemo.agent.session.AgentSession;

/**
 * Planner 接口(Agent Runtime V3).
 *
 * <p>根据会话上下文和用户问题生成执行计划。
 *
 * @since 0.0.1
 */
public interface AgentPlanner {

    /**
     * 生成执行计划.
     *
     * @param session  当前会话(含 memory / trace / metadata)
     * @param question 用户问题
     * @return 执行计划,不可为 null
     */
    AgentPlan plan(AgentSession session, String question);
}
