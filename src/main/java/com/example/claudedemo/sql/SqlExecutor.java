package com.example.claudedemo.sql;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * SQL 执行器 V1.
 *
 * <p>基于 JdbcTemplate，仅执行已校验的 SQL（{@link ValidatedSql}）。
 * 强制 queryTimeout=5 秒、maxRows=1000 行。
 *
 * <p>V1 不提供 execute(String) 重载：入参必须是 ValidatedSql，避免绕过校验。
 *
 * @author claude-code
 * @since 0.0.1
 */
@Component
public class SqlExecutor {

    /** SQL 执行超时时间（秒）. */
    private static final int QUERY_TIMEOUT_SECONDS = 5;

    /** 单次查询最大返回行数. */
    private static final int MAX_ROWS = 1000;

    private final JdbcTemplate jdbcTemplate;

    public SqlExecutor(DataSource dataSource) {
        // 自己拥有 JdbcTemplate，避免污染共享 Bean
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.jdbcTemplate.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
        this.jdbcTemplate.setMaxRows(MAX_ROWS);
    }

    /**
     * 执行已校验的 SQL 查询.
     *
     * @param validatedSql 校验通过的 SQL
     * @return 查询结果（行 → 列名 → 值）
     * @throws NullPointerException     当 validatedSql 为 null
     * @throws org.springframework.dao.DataAccessException 当 SQL 执行失败
     */
    public List<Map<String, Object>> execute(ValidatedSql validatedSql) {
        Objects.requireNonNull(validatedSql, "validatedSql must not be null");
        return jdbcTemplate.queryForList(validatedSql.sql());
    }
}
