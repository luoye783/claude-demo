package com.example.claudedemo.agent.planner.deviation;

import com.example.claudedemo.agent.planner.AgentPlan;

import java.util.List;

/**
 * 计划偏差检测器接口(Agent Runtime V6).
 *
 * <p>对比 {@link AgentPlan} 和实际
 * {@link ToolExecutionRecord} 列表,检测执行偏差。
 *
 * @since 0.0.1
 */
public interface PlanDeviationDetector {

    /**
     * 检测计划偏差.
     *
     * @param plan           执行后的计划(含 step 最终状态)
     * @param toolExecutions 实际工具调用记录列表
     * @return 偏差列表,无偏差时返回空列表
     */
    List<PlanDeviation> detect(AgentPlan plan, List<ToolExecutionRecord> toolExecutions);
}
