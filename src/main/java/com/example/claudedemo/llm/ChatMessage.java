package com.example.claudedemo.llm;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * LLM 对话消息.
 *
 * <p>OpenAI 协议角色：user / assistant / system.
 *
 * <p><b>Tool Calling 扩展</b>（V1 新增）：支持 OpenAI tool calling 协议。
 * <ul>
 *   <li>{@link #toolCalls}：仅 assistant 角色携带，LLM 请求调用工具</li>
 *   <li>{@link #toolCallId}：仅 tool 角色携带，对应 assistant {@code tool_calls[].id}</li>
 * </ul>
 * 二者通过 {@code @JsonInclude(NON_NULL)} 在序列化时自动剔除，正常 user/system/assistant
 * 消息与 V1 线缆格式完全一致，不会污染 V1 调用。
 *
 * <p><b>构造器兼容</b>：保留 {@code (role, content)} 二参构造器,V1 调用方零改动;
 * 需要工具调用时使用 {@code (role, content, toolCalls, toolCallId)} 四参构造器。
 *
 * @param role       角色
 * @param content    消息内容
 * @param toolCalls  assistant 消息携带的工具调用列表(V1 与 tool 角色为 {@code null})
 * @param toolCallId tool 消息携带的调用 id(V1 与 user/system/assistant 角色为 {@code null})
 * @author claude-code
 * @since 0.0.1
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatMessage(
        String role,
        String content,
        @JsonProperty("tool_calls") List<ToolCall> toolCalls,
        @JsonProperty("tool_call_id") String toolCallId
) {

    /**
     * V1 兼容构造器:普通文本消息.
     *
     * <p>等价于 {@code this(role, content, null, null)};序列化时
     * {@code tool_calls} / {@code tool_call_id} 字段因 {@code @JsonInclude(NON_NULL)} 不会出现在 JSON 中。
     */
    public ChatMessage(String role, String content) {
        this(role, content, null, null);
    }
}
