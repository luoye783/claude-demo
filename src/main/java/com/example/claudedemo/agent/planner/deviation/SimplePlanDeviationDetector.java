package com.example.claudedemo.agent.planner.deviation;

import com.example.claudedemo.agent.planner.AgentPlan;
import com.example.claudedemo.agent.planner.AgentPlanStep;
import com.example.claudedemo.agent.planner.AgentPlanStepStatus;
import com.example.claudedemo.agent.planner.AgentPlanStepType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 基于规则的简单计划偏差检测器(Agent Runtime V6).
 *
 * <p>五条检测规则:
 * <ol>
 *   <li>MISSING_TOOL_CALL — 计划要求的工具未调用</li>
 *   <li>UNPLANNED_TOOL_CALL — 调用了计划外工具</li>
 *   <li>TOOL_STEP_FAILED — 工具执行失败</li>
 *   <li>PENDING_REQUIRED_STEP — 关键步骤仍未执行(不与 MISSING_TOOL_CALL 重复)</li>
 *   <li>TOOL_ORDER_MISMATCH — 成功调用的计划内工具顺序不一致</li>
 * </ol>
 *
 * @since 0.0.1
 */
public class SimplePlanDeviationDetector implements PlanDeviationDetector {

    private static final Logger log = LoggerFactory.getLogger(SimplePlanDeviationDetector.class);

    @Override
    public List<PlanDeviation> detect(AgentPlan plan, List<ToolExecutionRecord> toolExecutions) {
        List<PlanDeviation> deviations = new ArrayList<>();

        if (plan == null) return deviations;
        List<ToolExecutionRecord> executions = toolExecutions == null ? List.of() : toolExecutions;

        String planId = plan.planId();
        Set<String> executedToolNames = executions.stream()
                .map(ToolExecutionRecord::toolName)
                .collect(Collectors.toSet());
        Set<String> failedToolNames = executions.stream()
                .filter(e -> !e.success())
                .map(ToolExecutionRecord::toolName)
                .collect(Collectors.toSet());

        // 1. MISSING_TOOL_CALL
        Set<String> plannedTools = plan.steps().stream()
                .filter(s -> s.type() == AgentPlanStepType.CALL_TOOL)
                .map(AgentPlanStep::expectedToolName)
                .collect(Collectors.toSet());
        Set<String> missingTools = new java.util.LinkedHashSet<>();
        for (AgentPlanStep step : plan.steps()) {
            if (step.type() != AgentPlanStepType.CALL_TOOL) continue;
            String tool = step.expectedToolName();
            if (tool != null && !executedToolNames.contains(tool)) {
                missingTools.add(tool);
                deviations.add(new PlanDeviation(planId, step.stepId(),
                        PlanDeviationType.MISSING_TOOL_CALL, PlanDeviationSeverity.WARN,
                        "计划要求调用 " + tool + "，但实际未调用"));
            }
        }

        // 2. UNPLANNED_TOOL_CALL
        for (String tool : executedToolNames) {
            if (!plannedTools.contains(tool)) {
                deviations.add(new PlanDeviation(planId, null,
                        PlanDeviationType.UNPLANNED_TOOL_CALL, PlanDeviationSeverity.WARN,
                        "实际调用了计划外工具 " + tool));
            }
        }

        // 3. TOOL_STEP_FAILED
        for (ToolExecutionRecord exec : executions) {
            if (!exec.success()) {
                deviations.add(new PlanDeviation(planId, null,
                        PlanDeviationType.TOOL_STEP_FAILED, PlanDeviationSeverity.ERROR,
                        "工具 " + exec.toolName() + " 执行失败"
                                + (exec.errorMessage() != null ? ": " + exec.errorMessage() : "")));
            }
        }

        // 4. PENDING_REQUIRED_STEP(补充偏差:工具已调用但 step 状态未更新)
        for (AgentPlanStep step : plan.steps()) {
            if (step.type() != AgentPlanStepType.CALL_TOOL) continue;
            if (step.status() != AgentPlanStepStatus.PENDING) continue;
            String tool = step.expectedToolName();
            if (tool != null && executedToolNames.contains(tool)) {
                deviations.add(new PlanDeviation(planId, step.stepId(),
                        PlanDeviationType.PENDING_REQUIRED_STEP, PlanDeviationSeverity.WARN,
                        "计划步骤 " + step.stepId() + "(" + tool + ") 已调用但状态未更新为完成"));
            }
        }

        // 5. TOOL_ORDER_MISMATCH(仅成功计划内工具调用)
        List<String> plannedOrder = plan.steps().stream()
                .filter(s -> s.type() == AgentPlanStepType.CALL_TOOL)
                .filter(s -> s.expectedToolName() != null
                        && !missingTools.contains(s.expectedToolName())
                        && !failedToolNames.contains(s.expectedToolName()))
                .map(AgentPlanStep::expectedToolName)
                .distinct()
                .toList();

        List<String> actualOrder = executions.stream()
                .filter(ToolExecutionRecord::success)
                .filter(e -> plannedTools.contains(e.toolName()))
                .map(ToolExecutionRecord::toolName)
                .distinct()
                .toList();

        if (plannedOrder.size() >= 2 && actualOrder.size() >= 2
                && !isSubOrderMatch(plannedOrder, actualOrder)) {
            deviations.add(new PlanDeviation(planId, null,
                    PlanDeviationType.TOOL_ORDER_MISMATCH, PlanDeviationSeverity.WARN,
                    "工具调用顺序与计划不一致: 计划=" + plannedOrder + " 实际=" + actualOrder));
        }

        log.debug("SimplePlanDeviationDetector: {} 个偏差", deviations.size());
        return List.copyOf(deviations);
    }

    /**
     * 检查 actual 是否是 planned 的子序列(保持相对顺序).
     * 只比较两者都包含的工具,actual 中计划外工具跳过.
     */
    private boolean isSubOrderMatch(List<String> planned, List<String> actual) {
        // 找出 actual 中在 planned 里的工具,检查顺序
        List<String> filtered = new ArrayList<>();
        for (String tool : actual) {
            if (planned.contains(tool)) {
                filtered.add(tool);
            }
        }
        if (filtered.size() < 2) return true; // 少于 2 个无需比较顺序

        int prevIdx = -1;
        for (String tool : filtered) {
            int idx = planned.indexOf(tool);
            if (idx < prevIdx) return false; // 顺序不一致
            prevIdx = idx;
        }
        return true;
    }
}
