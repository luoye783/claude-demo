package com.example.claudedemo.mcp.server;

import io.modelcontextprotocol.json.schema.JsonSchemaValidator;
import io.modelcontextprotocol.json.schema.JsonSchemaValidatorSupplier;

import java.util.Map;

/**
 * 最小可用 {@link JsonSchemaValidator} 实现.
 *
 * <p><b>V1 行为</b>:对所有入参返回 "valid",不实际校验 JSON Schema.
 *
 * <p>原因:
 * <ul>
 *   <li>MCP 1.1.3 内部 {@code McpJsonDefaults} 要求 classpath 上有
 *       {@link JsonSchemaValidatorSupplier} SPI 实现,否则 {@code McpServer.build()} 抛
 *       {@code ServiceConfigurationError}</li>
 *   <li>官方提供的实现 {@code JacksonJsonSchemaValidator} 依赖 Jackson 3 + {@code com.networknt:json-schema-validator},
 *       与本项目 Jackson 2 选型冲突</li>
 *   <li>Step 1 工具列表为空,不存在输入校验场景;Step 2/3 可在 tool handler 内手动校验,
 *       真正的 schema 校验在 V2 引入</li>
 * </ul>
 *
 * <p>替换路径:升级 MCP 2.x 并使用官方 Jackson 3 实现的 validator.
 *
 * @author claude-code
 * @since 0.0.1
 */
public class DefaultJsonSchemaValidator implements JsonSchemaValidator, JsonSchemaValidatorSupplier {

    @Override
    public ValidationResponse validate(Map<String, Object> schema, Object data) {
        // V1:不校验,直接返回 valid
        // TODO(Step 2+):集成 jsqlparser/手动校验工具入参
        return ValidationResponse.asValid(null);
    }

    @Override
    public JsonSchemaValidator get() {
        return this;
    }
}
