package com.example.claudedemo.agent;

import com.example.claudedemo.sql.ColumnInfo;
import com.example.claudedemo.sql.SchemaIntrospector;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Schema 选择器：根据 question 选取相关的 schema 文本.
 *
 * <p><b>V1 实现</b>：返回当前 schema 下的<b>所有</b>表结构（不做过滤）。
 *
 * <p><b>V2 扩展点</b>（保留不实现）：
 * <ul>
 *   <li>基于 question 关键词匹配表名/列名</li>
 *   <li>结合向量数据库做语义相似度检索</li>
 *   <li>按表大小、查询频率做优先级排序</li>
 * </ul>
 *
 * @author claude-code
 * @since 0.0.1
 */
@Component
public class SchemaSelector {

    private final SchemaIntrospector schemaIntrospector;

    public SchemaSelector(SchemaIntrospector schemaIntrospector) {
        this.schemaIntrospector = schemaIntrospector;
    }

    /**
     * 选择与问题相关的 schema 文本.
     *
     * @param question 用户问题（V1 不参与过滤，保留参数供 V2 扩展）
     * @return schema 文本，格式示例：
     * <pre>
     * 表 USERS:
     *   - ID (INTEGER, NOT NULL, "User ID")
     *   - NAME (CHARACTER VARYING, NOT NULL)
     * </pre>
     */
    public String select(String question) {
        List<String> tables = schemaIntrospector.listTables();
        if (tables.isEmpty()) {
            return "（空数据库）";
        }
        return tables.stream()
                .map(this::describeTable)
                .collect(Collectors.joining("\n\n"));
    }

    private String describeTable(String tableName) {
        List<ColumnInfo> columns = schemaIntrospector.describeTable(tableName);
        StringBuilder sb = new StringBuilder("表 ").append(tableName).append(":\n");
        for (ColumnInfo col : columns) {
            sb.append("  - ").append(col.name()).append(" (").append(col.type());
            if (!col.nullable()) sb.append(", NOT NULL");
            if (col.comment() != null) sb.append(", \"").append(col.comment()).append("\"");
            sb.append(")\n");
        }
        return sb.toString();
    }
}
