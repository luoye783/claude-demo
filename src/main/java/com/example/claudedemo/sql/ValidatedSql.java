package com.example.claudedemo.sql;

/**
 * SQL 校验结果.
 *
 * @param sql           注入 LIMIT 后的 SQL
 * @param appliedLimit  实际生效的 LIMIT
 * @param limitInjected 是否注入了默认 LIMIT
 * @author claude-code
 * @since 0.0.1
 */
public record ValidatedSql(
        String sql,
        int appliedLimit,
        boolean limitInjected
) {
}
