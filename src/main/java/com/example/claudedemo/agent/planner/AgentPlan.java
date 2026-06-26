package com.example.claudedemo.agent.planner;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Planner 生成的执行计划(Agent Runtime V3).
 *
 * @param planId      计划 ID(UUID)
 * @param question    用户问题
 * @param steps       执行步骤列表
 * @param createdAtMs 创建时间戳
 * @since 0.0.1
 */
public record AgentPlan(
        String planId,
        String question,
        List<AgentPlanStep> steps,
        long createdAtMs
) {

    public AgentPlan {
        if (planId == null || planId.isBlank()) {
            planId = UUID.randomUUID().toString();
        }
        steps = (steps == null) ? List.of() : List.copyOf(steps);
    }

    public AgentPlan(String question, List<AgentPlanStep> steps) {
        this(UUID.randomUUID().toString(), question, steps, System.currentTimeMillis());
    }

    /**
     * 更新指定 step 的 status,返回新的 AgentPlan(V4 不可变状态流转).
     *
     * <p>stepId 不存在时返回原 plan(this).
     */
    public AgentPlan updateStepStatus(String stepId, AgentPlanStepStatus newStatus) {
        List<AgentPlanStep> updated = new ArrayList<>(steps.size());
        boolean found = false;
        for (AgentPlanStep s : steps) {
            if (s.stepId().equals(stepId)) {
                updated.add(s.withStatus(newStatus));
                found = true;
            } else {
                updated.add(s);
            }
        }
        if (!found) return this;
        return new AgentPlan(planId, question, updated, createdAtMs);
    }

    /**
     * 生成人类可读的计划摘要,供 trace content 使用.
     *
     * @return 如 "Plan[abc123] 4 steps: RETRIEVE_CONTEXT → CALL_TOOL(get_schema) → CALL_TOOL(execute_sql) → GENERATE_ANSWER"
     */
    public String summary() {
        String arrow = " → ";
        String stepsStr = steps.stream()
                .map(s -> {
                    String label = s.type().name();
                    if (s.expectedToolName() != null) {
                        label += "(" + s.expectedToolName() + ")";
                    }
                    return label;
                })
                .collect(Collectors.joining(arrow));
        return "Plan[" + planId.substring(0, Math.min(8, planId.length())) + "] "
                + steps.size() + " steps: " + stepsStr;
    }
}
