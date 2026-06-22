package com.example.claudedemo.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * LLM 发起的工具调用请求.
 *
 * <p>OpenAI 协议线缆结构:
 * <pre>{@code
 * {
 *   "id": "call_abc123",
 *   "type": "function",
 *   "function": {
 *     "name": "execute_sql",
 *     "arguments": "{\"sql\": \"SELECT * FROM users\"}"
 *   }
 * }
 * }</pre>
 *
 * <p>使用嵌套 {@link Function} 匹配 OpenAI 线缆结构,可由 Jackson 直接反序列化,
 * 未知字段(如 {@code type} 未来新增的取值)会被 {@link JsonIgnoreProperties} 忽略,
 * 后续扩展时不需要为此修改本类。
 *
 * @param id       OpenAI 生成的调用 id,Java 侧回填 tool 消息时需透传
 * @param type     固定为 {@code "function"}(OpenAI V1 协议)
 * @param function 工具函数定义(name + arguments)
 * @author claude-code
 * @since 0.0.1
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ToolCall(
        String id,
        String type,
        Function function
) {

    /**
     * 工具函数定义.
     *
     * @param name      工具名(如 {@code "execute_sql"}),与 {@link ToolDefinition#name()} 对应
     * @param arguments LLM 生成的参数(JSON 字符串,如 {@code "{\"sql\": \"...\"}"})
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Function(String name, String arguments) {
    }
}
