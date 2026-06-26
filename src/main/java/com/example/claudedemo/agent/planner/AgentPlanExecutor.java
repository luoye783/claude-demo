package com.example.claudedemo.agent.planner;

import com.example.claudedemo.agent.session.AgentSession;
import com.example.claudedemo.agent.trace.StepType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 计划步骤执行器(Agent Runtime V4).
 *
 * <p>负责 AgentPlanStep 的状态流转和 trace 记录。
 * <b>V4 不实际执行工具调用</b>——工具调用仍由原 tool calling 主循环负责。
 *
 * <p>两个入口:
 * <ul>
 *   <li>{@link #executeBeforeMainLoop} — 主循环前,将非 GENERATE_ANSWER 步骤标记完成</li>
 *   <li>{@link #finalizeAfterMainLoop} — 主循环后,将 GENERATE_ANSWER 标记 SUCCESS/FAILED</li>
 * </ul>
 *
 * @since 0.0.1
 */
public class AgentPlanExecutor {

    private static final Logger log = LoggerFactory.getLogger(AgentPlanExecutor.class);

    /** CALL_TOOL 等步骤被跳过时的说明. */
    private static final String NOTE_TOOL_SKIPPED =
            "工具调用由原主 tool loop 负责，PlanExecutor V4 不直接执行工具";

    /**
     * 主循环前的计划执行:对每个 step 做状态流转 + trace.
     *
     * <ul>
     *   <li>RETRIEVE_CONTEXT → RUNNING → SUCCESS(buildInitialMessages 已处理 RAG)</li>
     *   <li>CALL_TOOL → RUNNING → SKIPPED(原 tool loop 负责)</li>
     *   <li>THINK → RUNNING → SKIPPED(V4 未实现)</li>
     *   <li>GENERATE_ANSWER → 保持 PENDING(留给 finalize)</li>
     * </ul>
     *
     * @param session  当前会话
     * @param plan     原始计划(所有 step 状态为 PENDING)
     * @param question 用户问题
     * @return 状态流转后的新 plan
     */
    public AgentPlan executeBeforeMainLoop(AgentSession session, AgentPlan plan, String question) {
        AgentPlan current = plan;

        for (AgentPlanStep step : plan.steps()) {
            AgentPlanStepStatus target;
            String note = null;

            switch (step.type()) {
                case RETRIEVE_CONTEXT -> {
                    target = AgentPlanStepStatus.SUCCESS;
                    note = "上下文检索由 buildInitialMessages 处理";
                }
                case CALL_TOOL -> {
                    target = AgentPlanStepStatus.SKIPPED;
                    note = NOTE_TOOL_SKIPPED;
                }
                case THINK -> {
                    target = AgentPlanStepStatus.SKIPPED;
                    note = "THINK 步骤 V4 未实现";
                }
                case GENERATE_ANSWER -> {
                    // 留给 finalizeAfterMainLoop
                    continue;
                }
                default -> { continue; }
            }

            // RUNNING → target
            current = transition(session, current, step, AgentPlanStepStatus.RUNNING, note);
            current = transition(session, current, step, target, note);
        }

        return current;
    }

    /**
     * 主循环后的计划收尾:将 GENERATE_ANSWER 标记 SUCCESS 或 FAILED.
     *
     * @param session 当前会话
     * @param plan    执行中的 plan
     * @param success 主循环是否成功
     * @return 收尾后的新 plan
     */
    public AgentPlan finalizeAfterMainLoop(AgentSession session, AgentPlan plan, boolean success) {
        AgentPlanStep answerStep = null;
        for (AgentPlanStep s : plan.steps()) {
            if (s.type() == AgentPlanStepType.GENERATE_ANSWER) {
                answerStep = s;
                break;
            }
        }
        if (answerStep == null) {
            return plan;
        }

        AgentPlanStepStatus target = success
                ? AgentPlanStepStatus.SUCCESS
                : AgentPlanStepStatus.FAILED;

        AgentPlan current = plan;
        current = transition(session, current, answerStep, AgentPlanStepStatus.RUNNING, null);
        current = transition(session, current, answerStep, target,
                success ? "主循环成功完成" : "主循环失败");
        return current;
    }

    // ==================== 内部 ====================

    /**
     * 单步状态流转:更新 plan → 写 trace.
     */
    private AgentPlan transition(AgentSession session, AgentPlan plan,
                                  AgentPlanStep step, AgentPlanStepStatus target, String note) {
        AgentPlan updated = plan.updateStepStatus(step.stepId(), target);
        if (updated != plan) {
            StepType traceType = (target == AgentPlanStepStatus.RUNNING)
                    ? StepType.PLAN_STEP_STARTED
                    : StepType.PLAN_STEP_FINISHED;

            String content = "planId=" + plan.planId().substring(0, Math.min(8, plan.planId().length()))
                    + " stepId=" + step.stepId()
                    + " type=" + step.type().name()
                    + (step.expectedToolName() != null ? " tool=" + step.expectedToolName() : "")
                    + " status=" + target.name();
            if (note != null) {
                content += " note=" + note;
            }
            session.trace().addStep(traceType, content);
        }
        return updated;
    }
}
