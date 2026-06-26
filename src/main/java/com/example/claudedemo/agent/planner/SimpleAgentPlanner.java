package com.example.claudedemo.agent.planner;

import com.example.claudedemo.agent.session.AgentSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 规则 Planner,不调用 LLM,生成固定计划(Agent Runtime V3).
 *
 * <p>对 NL2SQL 问题生成 4 步固定计划:
 * <ol>
 *   <li>RETRIEVE_CONTEXT — 检索或整理可用上下文</li>
 *   <li>CALL_TOOL(get_schema) — 获取相关表结构</li>
 *   <li>CALL_TOOL(execute_sql) — 执行生成的 SQL</li>
 *   <li>GENERATE_ANSWER — 生成最终答案</li>
 * </ol>
 *
 * <p><b>V1 定位</b>:纯可观测层,生成的计划不驱动实际执行流程;
 * 实际 tool calling 仍由 {@code Nl2SqlMcpAgent} 原有循环完成。
 *
 * @since 0.0.1
 */
public class SimpleAgentPlanner implements AgentPlanner {

    private static final Logger log = LoggerFactory.getLogger(SimpleAgentPlanner.class);

    @Override
    public AgentPlan plan(AgentSession session, String question) {
        List<AgentPlanStep> steps = new ArrayList<>();

        steps.add(new AgentPlanStep(
                "step-1", 1, AgentPlanStepType.RETRIEVE_CONTEXT,
                "检索或整理可用上下文，包括 memory / summary / RAG",
                null, AgentPlanStepStatus.PENDING));

        steps.add(new AgentPlanStep(
                "step-2", 2, AgentPlanStepType.CALL_TOOL,
                "获取相关数据库表结构",
                "get_schema", AgentPlanStepStatus.PENDING));

        steps.add(new AgentPlanStep(
                "step-3", 3, AgentPlanStepType.CALL_TOOL,
                "执行生成的 SQL 查询",
                "execute_sql", AgentPlanStepStatus.PENDING));

        steps.add(new AgentPlanStep(
                "step-4", 4, AgentPlanStepType.GENERATE_ANSWER,
                "基于 SQL 执行结果生成最终回答",
                null, AgentPlanStepStatus.PENDING));

        AgentPlan plan = new AgentPlan(question, steps);
        log.debug("SimpleAgentPlanner 生成计划: {}", plan.summary());
        return plan;
    }
}
