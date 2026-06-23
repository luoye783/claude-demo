# MCP Server V1 — Claude Code 对接指南

> Claude Code ↔ Java 进程的 MCP（STDIO 模式）桥接。
>
> 状态：**Step 4 已完成**，工具 `get_schema` / `execute_sql` 已注册，
> Claude Code 可直接通过 `/mcp` 调用。

---

## 一、项目目标

把 NL2SQL 工具 `get_schema` / `execute_sql` 通过 [MCP 协议](https://modelcontextprotocol.io/)
暴露给 Claude Code，让 Claude Code 直接调用数据库，不再依赖项目自身的 HTTP API。

**设计原则**：复用现有 `SchemaSelector` / `SqlValidator` / `SqlExecutor`，
**不改一行业务代码**；MCP server 只是 transport 适配层。

---

## 二、交付状态

| Step | 目标 | 状态 |
|------|------|------|
| 1 | 空 server 骨架，MCP 握手成功 | ✅ |
| 2 | 注册 `get_schema` tool | ✅ |
| 3 | 注册 `execute_sql` tool | ✅ |
| 4 | 固化 MCP 对接验证，编写文档 | ✅ |

---

## 三、Claude Code 调用指南

### 3.1 前置条件

- Java 21
- 已运行 `mvn compile`（确保代码是最新的）
- MySQL `claude_demo` 库可访问（配置见 `application.yml`）

### 3.2 启动 MCP Server

MCP Server 由 Claude Code 自动管理，**不需要手动启动**。
当你在 Claude Code 中执行 `/mcp` 时，Claude Code 会根据 `.mcp.json`
自动启停 Java 进程。

### 3.3 查看已注册的工具

在 Claude Code 中输入：

```
/mcp
```

终端会显示 MCP 连接状态和可用工具列表，正常应看到：

```
Connected MCP servers:
  claude-demo-db (2 tools)
```

### 3.4 调用 get_schema

获取数据库中所有表的结构（表名、字段名、字段类型、是否非空、注释）：

```
请调用 mcp__claude-demo-db__get_schema，查看数据库中有哪些表
```

Claude Code 会自动将 `get_schema` 工具返回的 schema 信息作为上下文，
后续的 SQL 相关问题可以基于此自动生成查询。

### 3.5 调用 execute_sql

执行 SELECT 查询，返回 JSON 格式结果：

```
请调用 mcp__claude-demo-db__execute_sql，执行：
SELECT * FROM users
```

返回示例：

```json
{
  "sql": "SELECT * FROM users LIMIT 100",
  "rowCount": 2,
  "rows": [
    {"id": 1, "name": "Alice", "age": 30},
    {"id": 2, "name": "Bob", "age": 25}
  ]
}
```

说明：
- `sql` — 实际执行的 SQL（自动注入 `LIMIT 100`）
- `rowCount` — 返回行数
- `rows` — 数据行列表

### 3.6 验证 DELETE 被拒绝

execute_sql 仅支持 SELECT 语句，DELETE / UPDATE / INSERT 会被拒绝：

```
请调用 mcp__claude-demo-db__execute_sql，执行：
DELETE FROM users
```

返回错误：

```
Error: 禁止 DML 操作（INSERT/UPDATE/DELETE）
```

### 3.7 在对话中自然使用

MCP 工具对 Claude Code 是透明的，可以在任务中自然描述需求，
Claude Code 会自主决定调用哪个工具。例如：

> 查询 users 表中有哪些城市的用户，统计每个城市的人数

Claude Code 会依次调用 `get_schema` 了解表结构，
然后调用 `execute_sql` 执行 `SELECT city, COUNT(*) FROM users GROUP BY city`。

---

## 四、.mcp.json 配置说明

项目根目录的 `.mcp.json` 是 MCP 服务器的注册文件，
Claude Code 启动时自动读取。

```json
{
  "mcpServers": {
    "claude-demo-db": {
      "command": "mvn",
      "args": [
        "-q",
        "exec:java",
        "-Dexec.mainClass=com.example.claudedemo.mcp.McpServerMain",
        "-Dspring.main.web-application-type=none",
        "-Dlogback.configurationFile=logback-mcp.xml"
      ]
    }
  }
}
```

| 字段 | 说明 |
|------|------|
| `mcpServers.claude-demo-db` | 服务器标识名，在 `/mcp` 列表中显示 |
| `command` | 启动命令，这里是 `mvn` |
| `args` | 启动参数，通过 `exec:java` 运行 `McpServerMain` 入口类 |
| `-Dspring.main.web-application-type=none` | 禁止内嵌 Tomcat（MCP 不是 Web 应用） |
| `-Dlogback.configurationFile=logback-mcp.xml` | 指定日志配置，强制所有日志走 stderr |

**MCP 协议独占 stdout**，所有日志必须走 stderr。
`logback-mcp.xml` 配置了 `ConsoleAppender` 的 `target=System.err`。

---

## 五、本地手动启动（调试用）

如需单独调试 MCP Server，不依赖 Claude Code：

```bash
mvn exec:java \
  -Dexec.mainClass=com.example.claudedemo.mcp.McpServerMain \
  -Dexec.cleanupDaemonThreads=false \
  -Dlogback.configurationFile=logback-mcp.xml
```

- 启动后 **stdout 保持静默**，等待 JSON-RPC 输入
- 日志持续输出到 **stderr**
- 按 `Ctrl+C` 关闭

---

## 六、目录结构

```
src/main/
├── java/com/example/claudedemo/mcp/
│   ├── McpServerMain.java              # 入口（非 @SpringBootApplication）
│   └── server/
│       ├── McpServerConfig.java        # @Configuration + @ComponentScan
│       ├── McpServerFactory.java       # 工厂：构造 McpSyncServer，注册工具
│       ├── McpServerMetadata.java      # 常量：SERVER_NAME / SERVER_VERSION
│       ├── Jackson2McpJsonMapper.java  # SPI：Jackson 2 → McpJsonMapper
│       └── DefaultJsonSchemaValidator.java  # SPI：schema 校验
└── resources/
    ├── logback-mcp.xml                 # 强制日志走 stderr
    └── META-INF/services/
        ├── io.modelcontextprotocol.json.McpJsonMapperSupplier
        └── io.modelcontextprotocol.json.schema.JsonSchemaValidatorSupplier
```

---

## 七、关键技术决策

### 7.1 不用 @SpringBootApplication

MCP server 入口 **`McpServerMain` 不带 `@SpringBootApplication` 注解**，而是用
`@Configuration` + `@ComponentScan` 的 `McpServerConfig` 作为 Spring 源。

**原因**：`@SpringBootApplication` 自带的 `@EnableAutoConfiguration` 的 `exclude` 属性
会被 Spring Boot Test 框架**全局收集**。当 classpath 上存在多个 `@SpringBootApplication`
时，它们的 `exclude` 会合并，污染其它测试上下文。

详见 `McpServerConfig` 类注释。

### 7.2 不用 mcp-json-jackson3，自己实现 JSON mapper

MCP 1.1.3 的 `mcp-json-jackson3` 内部用 `tools.jackson.databind`（Jackson 3）
访问 `com.fasterxml.jackson.annotation.JsonFormat$Shape.POJO`，运行时触发
`NoSuchFieldError`（Jackson 3 的 Shape 枚举无 POJO 字段）。

**修复**：排除 `mcp-json-jackson3`，自己写 `Jackson2McpJsonMapper`，复用
Spring Boot 自带的 Jackson 2 `ObjectMapper`。

### 7.3 DataSource 初始化

MCP 上下文由 `SpringApplicationBuilder.sources(McpServerConfig.class)` 启动，
**不开启 `@EnableAutoConfiguration`**。数据源由 `McpDataSourceConfig` 通过
`Environment` 读取 `application.yml` 的 `spring.datasource.*` 参数手动创建，
不依赖自动配置。

---

## 八、参考

- [MCP 官方文档](https://modelcontextprotocol.io/)
- [MCP Java SDK GitHub](https://github.com/modelcontextprotocol/java-sdk)（本项目锁版本 1.1.3）
- 项目 `CLAUDE.md` §十（Claude Code / MCP / Agent 学习约定）
