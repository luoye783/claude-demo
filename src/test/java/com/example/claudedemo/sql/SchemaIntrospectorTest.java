package com.example.claudedemo.sql;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@JdbcTest
@Import(SchemaIntrospector.class)
class SchemaIntrospectorTest {

    @Autowired
    private SchemaIntrospector schemaIntrospector;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        // 清理
        jdbcTemplate.execute("DROP TABLE IF EXISTS USERS");
        jdbcTemplate.execute("DROP TABLE IF EXISTS ORDERS");
        jdbcTemplate.execute("DROP TABLE IF EXISTS PRODUCTS");

        // 创建测试表：覆盖 NOT NULL / 可空 / 带备注 / 多类型
        jdbcTemplate.execute(
                "CREATE TABLE USERS ("
                        + "  id INT NOT NULL COMMENT 'User ID',"
                        + "  name VARCHAR(50) NOT NULL,"
                        + "  age INT,"
                        + "  email VARCHAR(100) COMMENT 'Email address'"
                        + ")"
        );
        jdbcTemplate.execute(
                "CREATE TABLE ORDERS (id INT, amount DECIMAL(10,2))"
        );
        jdbcTemplate.execute(
                "CREATE TABLE PRODUCTS (id INT, name VARCHAR(50))"
        );
    }

    @Test
    void should_list_all_tables() {
        List<String> tables = schemaIntrospector.listTables();
        assertThat(tables)
                .contains("USERS", "ORDERS", "PRODUCTS")
                .hasSize(3);
    }

    @Test
    void should_describe_table_columns_with_metadata() {
        List<ColumnInfo> columns = schemaIntrospector.describeTable("USERS");

        // 用 Map 索引便于按字段名查找
        Map<String, ColumnInfo> byName = columns.stream()
                .collect(Collectors.toMap(ColumnInfo::name, c -> c));

        assertThat(byName).containsOnlyKeys("ID", "NAME", "AGE", "EMAIL");

        // ID: NOT NULL + 有备注
        ColumnInfo id = byName.get("ID");
        assertThat(id.type()).isEqualTo("INTEGER");
        assertThat(id.nullable()).isFalse();
        assertThat(id.comment()).isEqualTo("User ID");

        // NAME: NOT NULL + 无备注
        ColumnInfo name = byName.get("NAME");
        assertThat(name.type()).isEqualTo("CHARACTER VARYING");
        assertThat(name.nullable()).isFalse();
        assertThat(name.comment()).isNull();

        // AGE: 可空 + 无备注
        ColumnInfo age = byName.get("AGE");
        assertThat(age.type()).isEqualTo("INTEGER");
        assertThat(age.nullable()).isTrue();
        assertThat(age.comment()).isNull();

        // EMAIL: 可空 + 有备注
        ColumnInfo email = byName.get("EMAIL");
        assertThat(email.type()).isEqualTo("CHARACTER VARYING");
        assertThat(email.nullable()).isTrue();
        assertThat(email.comment()).isEqualTo("Email address");
    }

    @Test
    void should_return_empty_for_unknown_table() {
        List<ColumnInfo> columns = schemaIntrospector.describeTable("UNKNOWN_TABLE");
        assertTrue(columns.isEmpty(), "不存在的表应返回空列表，不抛异常");
    }

    @Test
    void should_throw_on_null_table_name() {
        assertThrows(IllegalArgumentException.class,
                () -> schemaIntrospector.describeTable(null));
    }

    @Test
    void should_throw_on_empty_table_name() {
        assertThrows(IllegalArgumentException.class,
                () -> schemaIntrospector.describeTable(""));
        assertThrows(IllegalArgumentException.class,
                () -> schemaIntrospector.describeTable("   "));
    }
}
