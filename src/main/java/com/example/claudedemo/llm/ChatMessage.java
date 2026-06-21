package com.example.claudedemo.llm;

/**
 * LLM 对话消息.
 *
 * <p>OpenAI 协议角色：user / assistant / system.
 *
 * @param role    角色
 * @param content 消息内容
 * @author claude-code
 * @since 0.0.1
 */
public record ChatMessage(
        String role,
        String content
) {
}
