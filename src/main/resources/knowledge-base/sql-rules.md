# SQL 使用规则

使用 execute_sql 工具时的注意事项。

## 安全规则

1. execute_sql 只能执行只读 SELECT 语句。
2. 系统会自动注入 LIMIT 1000，防止全表扫描。
3. 所有 SQL 会经过 SqlValidator 校验，拒绝 DDL/DML 语句。

## 查询规范

- 禁止 SELECT *，必须显式列出字段名。
- 重要业务查询建议加 WHERE 条件限制范围。
- 分页查询由框架统一处理，不要手写 LIMIT offset。

## 错误处理

- 如果 SQL 被拒绝，会返回 Error 开头的结果。
- 拒绝原因包括：非 SELECT 语句、缺少 WHERE 条件、包含危险关键字。
