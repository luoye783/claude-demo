package com.example.claudedemo.llm;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * LLM 响应.
 *
 * <p>V1 字段最小集合:内容 + 结束原因。
 *
 * <p><b>Tool Calling 扩展</b>（V1 新增）：{@link #toolCalls} 用于承载 LLM 返回的工具调用请求,
 * 通过 {@code @JsonInclude(NON_NULL)} 在反序列化时仅在 OpenAI 响应包含 {@code tool_calls}
 * 字段时被填充,普通 V1 调用不受影响。
 *
 * <p><b>构造器兼容</b>：保留 {@code (content, finishReason)} 二参构造器,V1 调用方零改动;
 * 工具调用响应使用 {@code (content, finishReason, toolCalls)} 三参构造器。
 *
 * @param content      生成的文本内容(工具调用时可能为 {@code null})
 * @param finishReason 停止原因(stop / length / content_filter / tool_calls 等)
 * @param toolCalls    工具调用列表(V1 普通调用为 {@code null})
 * @author claude-code
 * @since 0.0.1
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LlmResponse(
        String content,
        String finishReason,
        @JsonProperty("tool_calls") List<ToolCall> toolCalls
) {

    /**
     * V1 兼容构造器:普通文本响应.
     *
     * <p>等价于 {@code this(content, finishReason, null)}。
     */
    public LlmResponse(String content, String finishReason) {
        this(content, finishReason, null);
    }
}
