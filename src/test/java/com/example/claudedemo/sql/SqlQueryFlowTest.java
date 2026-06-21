package com.example.claudedemo.sql;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 串联测试：验证 rawSql → SqlValidator → ValidatedSql → SqlExecutor 的完整链路.
 *
 * <p>关键约束：
 * <ul>
 *   <li>SqlExecutor 只接受 ValidatedSql，无法绕过校验</li>
 *   <li>SqlValidator 在测试内手动 new，不依赖 Spring 容器</li>
 * </ul>
 */
@JdbcTest
@Import(SqlExecutor.class)
class SqlQueryFlowTest {

    @Autowired
    private SqlExecutor sqlExecutor;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 手动 new SqlValidator：证明它是无依赖的纯类，可独立使用.
     */
    private final SqlValidator sqlValidator = new SqlValidator();

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS users");
        jdbcTemplate.execute("CREATE TABLE users (id INT, name VARCHAR(50), age INT)");
        jdbcTemplate.update("INSERT INTO users VALUES (1, 'Alice', 30)");
        jdbcTemplate.update("INSERT INTO users VALUES (2, 'Bob', 25)");
        jdbcTemplate.update("INSERT INTO users VALUES (3, 'Charlie', 35)");
    }

    // ========== 核心场景 1：自动注入 LIMIT 并成功执行 ==========

    @Test
    void should_auto_inject_limit_and_execute() {
        // rawSql 没有 LIMIT
        String rawSql = "SELECT * FROM users";

        // 1. 先校验
        ValidatedSql validatedSql = sqlValidator.validate(rawSql);
        assertEquals(100, validatedSql.appliedLimit(), "应自动注入 LIMIT 100");
        assertTrue(validatedSql.limitInjected(), "limitInjected 应为 true");

        // 2. 再执行
        List<Map<String, Object>> result = sqlExecutor.execute(validatedSql);

        // 3. 验证结果
        assertEquals(3, result.size());
    }

    // ========== 核心场景 2：DELETE 在 Validator 阶段就被拦截 ==========

    @Test
    void should_block_delete_at_validator_stage() {
        String rawSql = "DELETE FROM users";

        // 1. Validator 抛异常
        SqlValidationException ex = assertThrows(
                SqlValidationException.class,
                () -> sqlValidator.validate(rawSql)
        );
        assertEquals("SQL_DML_FORBIDDEN", ex.getCode());

        // 2. 数据原封不动（证明 SqlExecutor 根本没被调用，DELETE 没执行）
        Long rowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users", Long.class
        );
        assertEquals(3L, rowCount, "setUp 插入的 3 条数据应保持不变");
    }

    // ========== 补充场景：保留用户提供的 LIMIT ==========

    @Test
    void should_preserve_user_provided_limit() {
        String rawSql = "SELECT * FROM users LIMIT 5";

        ValidatedSql validatedSql = sqlValidator.validate(rawSql);
        assertEquals(5, validatedSql.appliedLimit(), "用户提供的 LIMIT 5 应被保留");
        assertFalse(validatedSql.limitInjected(), "未注入默认 LIMIT");

        List<Map<String, Object>> result = sqlExecutor.execute(validatedSql);
        assertEquals(3, result.size(), "表里只有 3 行，不足 5");
    }

    // ========== 补充场景：WHERE 条件 + 完整链路 ==========

    @Test
    void should_validate_then_execute_select_with_where() {
        String rawSql = "SELECT name FROM users WHERE age > 30";

        ValidatedSql validatedSql = sqlValidator.validate(rawSql);
        List<Map<String, Object>> result = sqlExecutor.execute(validatedSql);

        assertEquals(1, result.size());
        assertEquals("Charlie", result.get(0).get("NAME"));
    }

    // ========== 补充场景：UPDATE 也应在 Validator 阶段被拦截 ==========

    @Test
    void should_block_update_at_validator_stage() {
        String rawSql = "UPDATE users SET name = 'hacker'";

        SqlValidationException ex = assertThrows(
                SqlValidationException.class,
                () -> sqlValidator.validate(rawSql)
        );
        assertEquals("SQL_DML_FORBIDDEN", ex.getCode());

        // 数据未被篡改
        String name = jdbcTemplate.queryForObject(
                "SELECT name FROM users WHERE id = 1", String.class
        );
        assertEquals("Alice", name, "Alice 的名字应保持不变");
    }
}
