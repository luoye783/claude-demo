package com.example.claudedemo.agent;

import com.example.claudedemo.llm.ChatMessage;

import java.util.Collections;
import java.util.List;

/**
 * Tool Calling 循环耗尽最大轮次异常.
 *
 * <p>当 {@link Nl2SqlToolAgent} 在 {@value Nl2SqlToolAgent#MAX_ROUNDS} 轮内 LLM 始终
 * 返回 {@code tool_calls} 而未给出最终答案时抛出。
 *
 * <p>异常 message 故意不包含完整工具结果,仅含摘要与轮次,避免日志外泄;
 * 完整对话历史通过 {@link #getMessages()}、全部工具调用通过 {@link #getToolCalls()} 暴露,
 * 便于上层观测与排错。
 *
 * @author claude-code
 * @since 0.0.1
 */
public class ToolCallingExhaustedException extends RuntimeException {

    private final List<ChatMessage> messages;
    private final List<ToolCallRecord> toolCalls;

    public ToolCallingExhaustedException(int maxRounds, List<ChatMessage> messages, List<ToolCallRecord> toolCalls) {
        super("Tool Calling 失败:已执行 " + maxRounds + " 轮,LLM 始终未给出最终答案");
        this.messages = messages == null ? List.of() : List.copyOf(messages);
        this.toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
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
}
