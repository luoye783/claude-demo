package com.example.claudedemo.sql;

/**
 * 表字段元数据.
 *
 * @param name     字段名
 * @param type     字段类型（JDBC {@code TYPE_NAME}，如 "INT" / "VARCHAR"）
 * @param nullable 是否可空
 * @param comment  备注（可能为 null）
 * @author claude-code
 * @since 0.0.1
 */
public record ColumnInfo(
        String name,
        String type,
        boolean nullable,
        String comment
) {
}
