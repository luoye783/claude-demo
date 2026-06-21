package com.example.claudedemo.sql;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlValidatorTest {

    private final SqlValidator validator = new SqlValidator();

    // ========== 应通过：LIMIT 注入 ==========

    @Test
    void should_inject_limit_when_missing() {
        ValidatedSql r = validator.validate("SELECT * FROM users");
        assertAll(
                () -> assertTrue(r.sql().toUpperCase().contains("LIMIT 100")),
                () -> assertEquals(100, r.appliedLimit()),
                () -> assertTrue(r.limitInjected())
        );
    }

    @Test
    void should_preserve_where_clause() {
        ValidatedSql r = validator.validate("SELECT id, name FROM users WHERE age > 18");
        String sql = r.sql().toUpperCase();
        assertAll(
                () -> assertTrue(sql.contains("WHERE")),
                () -> assertTrue(sql.contains("AGE > 18")),
                () -> assertTrue(sql.contains("LIMIT 100"))
        );
    }

    // ========== 应通过：LIMIT 保留与边界 ==========

    @Test
    void should_keep_existing_limit() {
        ValidatedSql r = validator.validate("SELECT * FROM users LIMIT 5");
        assertAll(
                () -> assertTrue(r.sql().toUpperCase().contains("LIMIT 5")),
                () -> assertEquals(5, r.appliedLimit()),
                () -> assertFalse(r.limitInjected())
        );
    }

    @Test
    void should_accept_limit_at_max_boundary() {
        ValidatedSql r = validator.validate("SELECT * FROM users LIMIT 1000");
        assertAll(
                () -> assertTrue(r.sql().toUpperCase().contains("LIMIT 1000")),
                () -> assertEquals(1000, r.appliedLimit())
        );
    }

    // ========== 应拒绝：DML ==========

    @Test
    void should_reject_insert() {
        assertCode("INSERT INTO users VALUES (1)", "SQL_DML_FORBIDDEN");
    }

    @Test
    void should_reject_update() {
        assertCode("UPDATE users SET name = 'x'", "SQL_DML_FORBIDDEN");
    }

    @Test
    void should_reject_delete() {
        assertCode("DELETE FROM users", "SQL_DML_FORBIDDEN");
    }

    // ========== 应拒绝：DDL ==========

    @Test
    void should_reject_drop() {
        assertCode("DROP TABLE users", "SQL_DDL_FORBIDDEN");
    }

    @Test
    void should_reject_alter() {
        assertCode("ALTER TABLE users ADD COLUMN x INT", "SQL_DDL_FORBIDDEN");
    }

    @Test
    void should_reject_truncate() {
        assertCode("TRUNCATE TABLE users", "SQL_DDL_FORBIDDEN");
    }

    @Test
    void should_reject_create_table() {
        assertCode("CREATE TABLE x (id INT)", "SQL_DDL_FORBIDDEN");
    }

    // ========== 应拒绝：其他类型 ==========

    @Test
    void should_reject_show() {
        assertCode("SHOW TABLES", "SQL_UNSUPPORTED");
    }

    @Test
    void should_reject_explain() {
        assertCode("EXPLAIN SELECT * FROM users", "SQL_UNSUPPORTED");
    }

    // ========== 应拒绝：边界 ==========

    @Test
    void should_reject_empty_sql() {
        assertCode("", "SQL_EMPTY");
        assertCode("   ", "SQL_EMPTY");
        assertCode(null, "SQL_EMPTY");
    }

    @Test
    void should_reject_parse_error() {
        assertCode("SELEC * FORM users", "SQL_PARSE_ERROR");
    }

    @Test
    void should_reject_limit_exceeded() {
        assertCode("SELECT * FROM users LIMIT 1001", "SQL_LIMIT_EXCEEDED");
        assertCode("SELECT * FROM users LIMIT 9999", "SQL_LIMIT_EXCEEDED");
    }

    // ========== 工具方法 ==========

    private void assertCode(String sql, String expectedCode) {
        SqlValidationException ex = assertThrows(
                SqlValidationException.class,
                () -> validator.validate(sql)
        );
        assertEquals(expectedCode, ex.getCode(), "错误码不匹配");
    }
}
