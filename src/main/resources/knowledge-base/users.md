# users 表

users 表存储用户基础信息。

## 字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键，自增 |
| name | VARCHAR(50) | 用户昵称 |
| city | VARCHAR(50) | 用户所在城市 |
| status | TINYINT | 1=有效，0=禁用/未激活 |
| is_deleted | TINYINT | 0=未删除，1=已删除 |
| create_time | DATETIME | 创建时间 |
| update_time | DATETIME | 更新时间 |

## 常用查询

- 按城市统计用户数：`SELECT city, COUNT(*) FROM users WHERE is_deleted = 0 AND status = 1 GROUP BY city`
- 查询有效用户：`SELECT * FROM users WHERE is_deleted = 0 AND status = 1`
