package com.example.claudedemo.llm;

import java.util.Map;

/**
 * 工具定义:描述 LLM 可调用的一个工具.
 *
 * <p>由 {@link com.example.claudedemo.agent.tools.AgentTool#definition()} 返回,
 * 由 {@link LlmClient#chatWithTools} 包装为 OpenAI 线缆格式后随请求发送。
 *
 * <p>线缆结构(V1 Java 类型 → OpenAI JSON):
 * <pre>{@code
 * {
 *   "type": "function",
 *   "function": {
 *     "name": "get_schema",
 *     "description": "获取数据库表结构",
 *     "parameters": { "type": "object", "properties": {...}, "required": [...] }
 *   }
 * }
 * }</pre>
 *
 * <p>Java 类型故意只承载语义字段(name/description/parameters),由 LlmClient 负责包装线缆结构,
 * 避免上层代码直接构造 OpenAI 协议细节。
 *
 * @param name        工具名(V1 业务内唯一,如 {@code "get_schema"}、{@code "execute_sql"})
 * @param description 工具说明,LLM 依据此字段决定是否调用
 * @param parameters  参数定义,符合 JSON Schema 草案(V1 最少需含 {@code type} + {@code properties})
 * @author claude-code
 * @since 0.0.1
 */
public record ToolDefinition(
        String name,
        String description,
        Map<String, Object> parameters
) {
}
