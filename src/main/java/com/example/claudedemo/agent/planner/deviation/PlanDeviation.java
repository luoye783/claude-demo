package com.example.claudedemo.agent.planner.deviation;

import java.util.Map;
import java.util.UUID;

/**
 * 计划偏差记录(Agent Runtime V6).
 *
 * @param deviationId 偏差 ID(UUID)
 * @param planId      所属计划 ID
 * @param stepId      关联步骤 ID(可能为 null)
 * @param type        偏差类型
 * @param severity    严重级别
 * @param message     人类可读描述
 * @param createdAtMs 创建时间戳
 * @param metadata    扩展信息
 * @since 0.0.1
 */
public record PlanDeviation(
        String deviationId,
        String planId,
        String stepId,
        PlanDeviationType type,
        PlanDeviationSeverity severity,
        String message,
        long createdAtMs,
        Map<String, Object> metadata
) {

    public PlanDeviation {
        if (deviationId == null || deviationId.isBlank()) {
            deviationId = UUID.randomUUID().toString();
        }
        if (metadata == null) {
            metadata = Map.of();
        }
    }

    public PlanDeviation(String planId, String stepId, PlanDeviationType type,
                         PlanDeviationSeverity severity, String message) {
        this(UUID.randomUUID().toString(), planId, stepId, type, severity,
                message, System.currentTimeMillis(), Map.of());
    }
}
