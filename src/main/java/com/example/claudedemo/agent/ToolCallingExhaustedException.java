package com.example.claudedemo.agent;

import com.example.claudedemo.agent.trace.AgentTrace;
import com.example.claudedemo.llm.ChatMessage;

import java.util.Collections;
import java.util.List;

/**
 * Tool Calling 循环耗尽最大轮次异常.
 *
 * <p>当 {@link Nl2SqlToolAgent} / {@link com.example.claudedemo.agent.mcp.Nl2SqlMcpAgent}
 * 在 {@value Nl2SqlToolAgent#MAX_ROUNDS} 轮内 LLM 始终返回 {@code tool_calls}
 * 而未给出最终答案时抛出。
 *
 * <p>异常 message 故意不包含完整工具结果,仅含摘要与轮次,避免日志外泄;
 * 完整对话历史通过 {@link #getMessages()}、全部工具调用通过 {@link #getToolCalls()}
 * 与本次执行的 {@link #getTrace()} 暴露,便于上层观测与排错。
 *
 * @author claude-code
 * @since 0.0.1
 */
public class ToolCallingExhaustedException extends RuntimeException {

    private final List<ChatMessage> messages;
    private final List<ToolCallRecord> toolCalls;
    private final AgentTrace trace;

    /**
     * V1 兼容构造器:不携带 trace.
     */
    public ToolCallingExhaustedException(int maxRounds, List<ChatMessage> messages, List<ToolCallRecord> toolCalls) {
        this(maxRounds, messages, toolCalls, new AgentTrace());
    }

    /**
     * 含 trace 的完整构造器.
     */
    public ToolCallingExhaustedException(int maxRounds, List<ChatMessage> messages,
                                         List<ToolCallRecord> toolCalls, AgentTrace trace) {
        super("Tool Calling 失败:已执行 " + maxRounds + " 轮,LLM 始终未给出最终答案");
        this.messages = messages == null ? List.of() : List.copyOf(messages);
        this.toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        this.trace = trace == null ? new AgentTrace() : trace;
    }

    /**
     * 完整对话历史(不可变副本).
     */
    public List<ChatMessage> getMessages() {
        return Collections.unmodifiableList(messages);
    }

    /**
     * 全部工具调用记录(不可变副本).
     */
    public List<ToolCallRecord> getToolCalls() {
        return Collections.unmodifiableList(toolCalls);
    }

    /**
     * 本次执行的完整 trace(不可变,含 traceId 与所有步骤).
     */
    public AgentTrace getTrace() {
        return trace;
    }
}
