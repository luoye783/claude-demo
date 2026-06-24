package com.example.claudedemo.agent.memory;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 单轮对话记录.
 *
 * <p>与 Nl2SqlMcpAgent 的 answer(String, String) 一一对应:保存用户提问与 Agent 最终答案,
 * 以及提问时间戳。不含 tool_call / tool_result / schema / SQL 结果等中间状态。
 *
 * <p>序列化支持 Jackson,便于调试输出。
 *
 * @param question  用户原始问题
 * @param answer    Agent 最终自然语言答案
 * @param timestampMs 提问时间戳(System.currentTimeMillis())
 * @since 0.0.1
 */
public record ConversationTurn(
        @JsonProperty("question") String question,
        @JsonProperty("answer") String answer,
        @JsonProperty("timestampMs") long timestampMs
) {

    /**
     * 仅含 question 与 answer 的便捷构造器,自动填入 {@code System.currentTimeMillis()}.
     */
    public ConversationTurn(String question, String answer) {
        this(question, answer, System.currentTimeMillis());
    }
}
