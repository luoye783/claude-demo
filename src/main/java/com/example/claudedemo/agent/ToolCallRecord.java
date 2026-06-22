package com.example.claudedemo.agent;

import com.example.claudedemo.llm.ToolCall;

import java.util.List;

/**
 * 单次工具调用的完整记录(用于调试与回溯).
 *
 * <p>由 {@link Nl2SqlToolAgent} 在每次执行完一个工具后追加,
 * 最终通过 {@link ToolCallingResult#toolCalls()} 暴露全部调用历史。
 *
 * <p>对比 {@link ToolCall}：{@link ToolCall} 承载 LLM 请求侧的入参,
 * {@code ToolCallRecord} 在此基础上额外包含 Java 侧的执行结果,
 * 二者字段一一对应,便于排查 LLM 期望与 Java 实际执行的差异。
 *
 * @param toolCallId OpenAI 生成的调用 id(对应 tool 消息的 {@code tool_call_id})
 * @param toolName   工具名
 * @param arguments  LLM 传来的参数 JSON 字符串
 * @param result     Java 执行结果(对应回填到 tool 消息 content 的字符串)
 * @author claude-code
 * @since 0.0.1
 */
public record ToolCallRecord(
        String toolCallId,
        String toolName,
        String arguments,
        String result
) {

    /**
     * 从 {@link ToolCall} + Java 侧结果构造记录.
     */
    public static ToolCallRecord of(ToolCall call, String result) {
        String args = (call != null && call.function() != null) ? call.function().arguments() : "";
        String name = (call != null && call.function() != null) ? call.function().name() : null;
        return new ToolCallRecord(
                call != null ? call.id() : null,
                name,
                args == null ? "" : args,
                result == null ? "" : result
        );
    }

    /**
     * 列表不可变副本(用于填充 {@link ToolCallingResult#toolCalls()})
     */
    public static List<ToolCallRecord> copyOf(List<ToolCallRecord> records) {
        return records == null ? List.of() : List.copyOf(records);
    }
}
