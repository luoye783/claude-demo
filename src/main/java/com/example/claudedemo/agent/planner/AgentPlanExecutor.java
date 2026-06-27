package com.example.claudedemo.agent.planner;

import com.example.claudedemo.agent.session.AgentSession;
import com.example.claudedemo.agent.trace.StepType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 计划步骤执行器(Agent Runtime V5).
 *
 * <p>负责 AgentPlanStep 的状态流转和 trace 记录。
 * <b>V5 将 CALL_TOOL step 与真实 tool calling 过程关联</b>。
 *
 * <p>入口:
 * <ul>
 *   <li>{@link #executeBeforeMainLoop} — 主循环前,RETRIEVE_CONTEXT→SUCCESS, THINK→SKIPPED</li>
 *   <li>{@link #markToolStepStarted} — 真实工具调用前,CALL_TOOL→RUNNING</li>
 *   <li>{@link #markToolStepFinished} — 真实工具调用后,CALL_TOOL→SUCCESS/FAILED</li>
 *   <li>{@link #finalizeAfterMainLoop} — 主循环后,GENERATE_ANSWER→SUCCESS/FAILED</li>
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
     * 主循环前的计划执行.
     *
     * <ul>
     *   <li>RETRIEVE_CONTEXT → RUNNING → SUCCESS</li>
     *   <li>THINK → RUNNING → SKIPPED</li>
     *   <li>CALL_TOOL → 保持 PENDING(等真实 tool call)</li>
     *   <li>GENERATE_ANSWER → 保持 PENDING(留给 finalize)</li>
     * </ul>
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
                case THINK -> {
                    target = AgentPlanStepStatus.SKIPPED;
                    note = "THINK 步骤 V5 未实现";
                }
                case CALL_TOOL -> {
                    // V5: 保持 PENDING,等真实 tool call 时再流转
                    continue;
                }
                case GENERATE_ANSWER -> {
                    // 留给 finalizeAfterMainLoop
                    continue;
                }
                default -> { continue; }
            }

            current = transition(session, current, step, AgentPlanStepStatus.RUNNING,
                    null, note);
            current = transition(session, current, step, target,
                    null, note);
        }

        return current;
    }

    /**
     * 标记工具步骤开始执行(V5 新增).
     *
     * <p>匹配规则:
     * <ol>
     *   <li>type = CALL_TOOL 且 expectedToolName = toolName</li>
     *   <li>优先 status = PENDING,其次 SKIPPED</li>
     *   <li>不匹配 RUNNING / SUCCESS / FAILED</li>
     * </ol>
     *
     * @param session     当前会话
     * @param plan        当前计划
     * @param toolName    工具名(如 "get_schema")
     * @param toolCallId  LLM 返回的 tool_call id
     * @return 更新后的 plan,未匹配则返回原 plan
     */
    public AgentPlan markToolStepStarted(AgentSession session, AgentPlan plan,
                                          String toolName, String toolCallId) {
        AgentPlanStep match = findMatchingStep(plan, toolName,
                AgentPlanStepStatus.PENDING, AgentPlanStepStatus.SKIPPED);
        if (match == null) {
            log.debug("markToolStepStarted: 未找到可匹配 step, tool={}", toolName);
            return plan;
        }
        return transition(session, plan, match, AgentPlanStepStatus.RUNNING,
                toolCallId, "tool call started");
    }

    /**
     * 标记工具步骤执行完成(V5 新增).
     *
     * <p>匹配规则:type = CALL_TOOL 且 expectedToolName = toolName 且 status = RUNNING.
     *
     * @param success true→SUCCESS, false→FAILED
     * @param note    补充说明(成功时可为 null,失败时含异常信息)
     */
    public AgentPlan markToolStepFinished(AgentSession session, AgentPlan plan,
                                           String toolName, String toolCallId,
                                           boolean success, String note) {
        AgentPlanStep match = findMatchingStep(plan, toolName, AgentPlanStepStatus.RUNNING);
        if (match == null) {
            log.debug("markToolStepFinished: 未找到 RUNNING step, tool={}", toolName);
            return plan;
        }
        AgentPlanStepStatus target = success
                ? AgentPlanStepStatus.SUCCESS
                : AgentPlanStepStatus.FAILED;
        return transition(session, plan, match, target, toolCallId, note);
    }

    /**
     * 主循环后的计划收尾.
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
        current = transition(session, current, answerStep, AgentPlanStepStatus.RUNNING,
                null, null);
        current = transition(session, current, answerStep, target,
                null, success ? "主循环成功完成" : "主循环失败");
        return current;
    }

    // ==================== 内部 ====================

    /**
     * 在 plan.steps() 中查找匹配 step.
     *
     * @param plan         当前计划
     * @param toolName     目标工具名
     * @param preferred     优先匹配的 status
     * @param fallbacks     次选 status
     * @return 匹配的 step,未找到返回 null
     */
    private AgentPlanStep findMatchingStep(AgentPlan plan, String toolName,
                                            AgentPlanStepStatus preferred,
                                            AgentPlanStepStatus... fallbacks) {
        // 优先匹配 preferred
        for (AgentPlanStep s : plan.steps()) {
            if (s.type() == AgentPlanStepType.CALL_TOOL
                    && toolName.equals(s.expectedToolName())
                    && s.status() == preferred) {
                return s;
            }
        }
        // 其次匹配 fallback statuses
        for (AgentPlanStepStatus fb : fallbacks) {
            for (AgentPlanStep s : plan.steps()) {
                if (s.type() == AgentPlanStepType.CALL_TOOL
                        && toolName.equals(s.expectedToolName())
                        && s.status() == fb) {
                    return s;
                }
            }
        }
        return null;
    }

    /**
     * 单步状态流转:更新 plan → 写 trace.
     */
    private AgentPlan transition(AgentSession session, AgentPlan plan,
                                  AgentPlanStep step, AgentPlanStepStatus target,
                                  String toolCallId, String note) {
        AgentPlan updated = plan.updateStepStatus(step.stepId(), target);
        if (updated != plan) {
            StepType traceType = (target == AgentPlanStepStatus.RUNNING)
                    ? StepType.PLAN_STEP_STARTED
                    : StepType.PLAN_STEP_FINISHED;

            String content = buildTraceContent(plan, step, target, toolCallId, note);
            session.trace().addStep(traceType, content);
        }
        return updated;
    }

    /** 构建 trace content 字符串. */
    private String buildTraceContent(AgentPlan plan, AgentPlanStep step,
                                      AgentPlanStepStatus target,
                                      String toolCallId, String note) {
        StringBuilder sb = new StringBuilder(128);
        sb.append("planId=").append(plan.planId(), 0, Math.min(8, plan.planId().length()));
        sb.append(" stepId=").append(step.stepId());
        sb.append(" type=").append(step.type().name());
        if (step.expectedToolName() != null) {
            sb.append(" tool=").append(step.expectedToolName());
        }
        if (toolCallId != null) {
            sb.append(" toolCallId=").append(toolCallId);
        }
        sb.append(" status=").append(target.name());
        if (note != null) {
            sb.append(" note=").append(note);
        }
        return sb.toString();
    }
}
