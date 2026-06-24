package com.example.claudedemo.agent.trace;

/**
 * Agent Trace 单步记录.
 *
 * @param stepNo       步骤序号(从 1 开始递增)
 * @param timestampMs  时间戳(System.currentTimeMillis())
 * @param stepType     步骤类型
 * @param content      步骤内容(统一截断至 {@value #MAX_CONTENT_LENGTH} 字符)
 * @param durationMs   测量步骤耗时(非测量步骤为 0)
 * @author claude-code
 * @since 0.0.1
 */
public record TraceStep(
        int stepNo,
        long timestampMs,
        StepType stepType,
        String content,
        long durationMs
) {

    /** 内容最大长度. */
    private static final int MAX_CONTENT_LENGTH = 500;

    /**
     * 构造 TraceStep(自动截断 content).
     */
    public TraceStep(int stepNo, long timestampMs, StepType stepType, String content, long durationMs) {
        this.stepNo = stepNo;
        this.timestampMs = timestampMs;
        this.stepType = stepType;
        this.content = truncate(content);
        this.durationMs = durationMs;
    }

    private static String truncate(String s) {
        if (s == null) return "";
        if (s.length() <= MAX_CONTENT_LENGTH) return s;
        return s.substring(0, MAX_CONTENT_LENGTH) + "...";
    }
}
