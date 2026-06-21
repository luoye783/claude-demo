package com.example.claudedemo.llm;

/**
 * LLM 响应.
 *
 * <p>V1 字段最小集合：内容 + 结束原因。后续可加 usage / model / id 等。
 *
 * @param content      生成的文本内容
 * @param finishReason 停止原因（stop / length / content_filter 等）
 * @author claude-code
 * @since 0.0.1
 */
public record LlmResponse(
        String content,
        String finishReason
) {
}
