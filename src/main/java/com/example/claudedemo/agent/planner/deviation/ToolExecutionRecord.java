package com.example.claudedemo.agent.planner.deviation;

/**
 * 真实工具调用记录(Agent Runtime V6).
 *
 * <p>在 tool loop 中每次 MCP 工具调用完成后产生,
 * 用于偏差检测和审计。
 *
 * @param toolCallId   LLM 返回的 tool_call id
 * @param toolName     工具名(如 "get_schema")
 * @param order        调用顺序(1-based,全局递增)
 * @param success      是否成功
 * @param startedAtMs  开始时间戳
 * @param finishedAtMs 结束时间戳
 * @param errorMessage 失败时的错误信息,成功时为 null
 * @since 0.0.1
 */
public record ToolExecutionRecord(
        String toolCallId,
        String toolName,
        int order,
        boolean success,
        long startedAtMs,
        long finishedAtMs,
        String errorMessage
) {
}
