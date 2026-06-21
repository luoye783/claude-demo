package com.example.claudedemo.sql;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@JdbcTest
@Import(SqlExecutor.class)
class SqlExecutorTest {

    @Autowired
    private SqlExecutor sqlExecutor;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS users");
        jdbcTemplate.execute("CREATE TABLE users (id INT, name VARCHAR(50), age INT)");
        jdbcTemplate.update("INSERT INTO users VALUES (1, 'Alice', 30)");
        jdbcTemplate.update("INSERT INTO users VALUES (2, 'Bob', 25)");
        jdbcTemplate.update("INSERT INTO users VALUES (3, 'Charlie', 35)");
    }

    @Test
    void should_execute_select_returning_all_rows() {
        ValidatedSql sql = new ValidatedSql("SELECT * FROM users", 100, false);
        List<Map<String, Object>> result = sqlExecutor.execute(sql);
        assertEquals(3, result.size());
    }

    @Test
    void should_execute_select_with_where() {
        ValidatedSql sql = new ValidatedSql("SELECT * FROM users WHERE age > 30", 100, false);
        List<Map<String, Object>> result = sqlExecutor.execute(sql);
        assertEquals(1, result.size());
        assertEquals("Charlie", result.get(0).get("NAME"));
    }

    @Test
    void should_execute_aggregate() {
        ValidatedSql sql = new ValidatedSql("SELECT COUNT(*) FROM users", 100, false);
        List<Map<String, Object>> result = sqlExecutor.execute(sql);
        assertEquals(1, result.size());
        // H2 返回 COUNT 为 Long，转 Number 兼容
        Object count = result.get(0).values().iterator().next();
        assertEquals(3, ((Number) count).intValue());
    }

    @Test
    void should_return_empty_for_no_matches() {
        ValidatedSql sql = new ValidatedSql("SELECT * FROM users WHERE id = 999", 100, false);
        List<Map<String, Object>> result = sqlExecutor.execute(sql);
        assertTrue(result.isEmpty());
    }

    @Test
    void should_limit_max_rows_to_1000() {
        // 插入 1500 行（含 setUp 中的 3 行，共 1503 行）
        for (int i = 100; i < 1600; i++) {
            jdbcTemplate.update("INSERT INTO users VALUES (?, ?, ?)", i, "user" + i, 20);
        }
        ValidatedSql sql = new ValidatedSql("SELECT * FROM users", 100, false);
        List<Map<String, Object>> result = sqlExecutor.execute(sql);
        assertEquals(1000, result.size(), "maxRows 应限制为 1000");
    }

    @Test
    void should_apply_query_timeout_5_seconds() throws Exception {
        JdbcTemplate template = extractJdbcTemplate();
        assertEquals(5, template.getQueryTimeout());
    }

    @Test
    void should_throw_on_null_validated_sql() {
        assertThrows(NullPointerException.class, () -> sqlExecutor.execute(null));
    }

    /**
     * 反射读取 JdbcTemplate，用于验证配置.
     * V1 阶段 SqlExecutor 不暴露 getter，仅供测试使用.
     */
    private JdbcTemplate extractJdbcTemplate() throws Exception {
        Field field = SqlExecutor.class.getDeclaredField("jdbcTemplate");
        field.setAccessible(true);
        return (JdbcTemplate) field.get(sqlExecutor);
    }
}
