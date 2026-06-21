package com.example.claudedemo.sql;

import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Schema 探查器 V1.
 *
 * <p>基于 JDBC {@link DatabaseMetaData} 读取数据库元数据。
 * H2 / MySQL / Oracle 等均通用，无需为不同数据库维护多套 SQL。
 *
 * <p>V1 能力：
 * <ul>
 *   <li>{@link #listTables()} 列出当前 schema 下的所有表名</li>
 *   <li>{@link #describeTable(String)} 描述单表字段信息</li>
 * </ul>
 *
 * <p><b>重要</b>：通过 {@code conn.getSchema()} 限定到当前 schema，
 * 避免 H2 / MySQL 的 INFORMATION_SCHEMA 系统表污染结果。
 *
 * @author claude-code
 * @since 0.0.1
 */
@Component
public class SchemaIntrospector {

    /** 只查询普通表（不含 VIEW / SYSTEM TABLE 等）. */
    private static final String[] TABLE_TYPES = {"TABLE"};

    /** H2 默认 schema. */
    private static final String DEFAULT_SCHEMA = "PUBLIC";

    private final DataSource dataSource;

    public SchemaIntrospector(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * 列出当前 schema 下的所有表名.
     *
     * @return 表名列表（H2 默认大写、MySQL 默认小写，V1 不做归一化）
     */
    public List<String> listTables() {
        try (Connection conn = dataSource.getConnection()) {
            String schema = resolveSchema(conn);
            try (ResultSet rs = conn.getMetaData().getTables(null, schema, "%", TABLE_TYPES)) {
                List<String> tables = new ArrayList<>();
                while (rs.next()) {
                    tables.add(rs.getString("TABLE_NAME"));
                }
                return tables;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to list tables", e);
        }
    }

    /**
     * 描述某张表的字段信息.
     *
     * @param tableName 表名（大小写需与 listTables() 返回的一致）
     * @return 字段信息列表（按 DB 返回顺序）
     * @throws IllegalArgumentException 当 tableName 为 null 或空白
     */
    public List<ColumnInfo> describeTable(String tableName) {
        if (tableName == null || tableName.trim().isEmpty()) {
            throw new IllegalArgumentException("tableName must not be null or empty");
        }
        try (Connection conn = dataSource.getConnection()) {
            String schema = resolveSchema(conn);
            try (ResultSet rs = conn.getMetaData().getColumns(null, schema, tableName, "%")) {
                List<ColumnInfo> columns = new ArrayList<>();
                while (rs.next()) {
                    columns.add(new ColumnInfo(
                            rs.getString("COLUMN_NAME"),
                            rs.getString("TYPE_NAME"),
                            rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable,
                            rs.getString("REMARKS")
                    ));
                }
                return columns;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to describe table: " + tableName, e);
        }
    }

    /**
     * 解析当前连接的有效 schema.
     *
     * <p>优先用 {@code conn.getSchema()}；若返回 null（H2 极端场景）则回退到 PUBLIC。
     */
    private String resolveSchema(Connection conn) throws SQLException {
        String schema = conn.getSchema();
        return (schema != null && !schema.isBlank()) ? schema : DEFAULT_SCHEMA;
    }
}
