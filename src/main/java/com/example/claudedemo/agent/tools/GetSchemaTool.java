package com.example.claudedemo.agent.tools;

import com.example.claudedemo.agent.SchemaSelector;
import com.example.claudedemo.llm.ToolDefinition;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * get_schema 工具:返回数据库所有表结构.
 *
 * <p>V1 不接受任何参数(LLM 通常首次调用以了解 schema);
 * 实际取值复用 {@link SchemaSelector},与 V1 Nl2SqlAgent 保持一致。
 *
 * @author claude-code
 * @since 0.0.1
 */
@Component
public class GetSchemaTool implements AgentTool {

    /** 工具名(V1 业务内唯一,作为 Agent 调度键). */
    public static final String NAME = "get_schema";

    private final SchemaSelector schemaSelector;

    public GetSchemaTool(SchemaSelector schemaSelector) {
        this.schemaSelector = schemaSelector;
    }

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(
                NAME,
                "获取数据库中所有表的结构(表名 + 字段名 + 字段类型 + 是否非空 + 字段注释)。"
                        + "必须作为首个工具调用,以便了解有哪些表可用。",
                Map.of(
                        "type", "object",
                        "properties", Map.of(),
                        "required", List.of()
                )
        );
    }

    @Override
    public String execute(String argumentsJson) {
        // V1 SchemaSelector 不基于 question 过滤,直接传空串即可
        return schemaSelector.select("");
    }
}
