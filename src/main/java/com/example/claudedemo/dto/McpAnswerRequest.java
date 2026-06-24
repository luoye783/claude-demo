package com.example.claudedemo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * MCP Agent 问答接口请求 DTO.
 *
 * <p>对应 {@code POST /api/mcp/answer}.
 *
 * @param question 用户问题(必填,1-2000 字符)
 * @author claude-code
 * @since 0.0.1
 */
public record McpAnswerRequest(
        @NotBlank(message = "question 不能为空")
        @Size(max = 2000, message = "question 长度不能超过 2000 字符")
        String question
) {
}
