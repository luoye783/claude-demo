package com.example.claudedemo.agent.trace;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Agent 执行轨迹记录器.
 *
 * <p>收集 {@link TraceStep} 列表,附带全局唯一 {@code traceId}.
 * 由 {@code Nl2SqlToolAgent} / {@code Nl2SqlMcpAgent} 在执行过程中埋点填充,
 * 最终附加到 {@link com.example.claudedemo.agent.ToolCallingResult} 或
 * {@link com.example.claudedemo.agent.ToolCallingExhaustedException} 中.
 *
 * @author claude-code
 * @since 0.0.1
 */
public class AgentTrace {

    private final String traceId;
    private final List<TraceStep> steps;
    private int counter;

    public AgentTrace() {
        this.traceId = UUID.randomUUID().toString();
        this.steps = new ArrayList<>();
        this.counter = 0;
    }

    /**
     * 全局唯一轨迹 ID.
     */
    public String traceId() {
        return traceId;
    }

    /**
     * 添加不耗时步骤(USER_QUESTION, FINAL_ANSWER 等).
     */
    public void addStep(StepType type, String content) {
        addStep(type, content, 0);
    }

    /**
     * 添加耗时步骤(LLM_RESPONSE, TOOL_RESULT 等).
     */
    public void addStep(StepType type, String content, long durationMs) {
        steps.add(new TraceStep(++counter, System.currentTimeMillis(), type, content, durationMs));
    }

    /**
     * 添加错误步骤.
     */
    public void addError(String content) {
        addStep(StepType.ERROR, content);
    }

    /**
     * 返回不可变副本.
     */
    public List<TraceStep> steps() {
        return List.copyOf(steps);
    }

    /**
     * 是否无任何步骤.
     */
    public boolean isEmpty() {
        return steps.isEmpty();
    }
}
