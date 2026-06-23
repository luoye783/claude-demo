# MCP Server V1

> Claude Code ↔ Java 进程的 MCP(STDIO 模式)桥接。
>
> 状态：**Step 1(空 server 骨架)已交付**;Step 2(get_schema)、Step 3(execute_sql)、Step 4(Claude Code 对接)待跟进。

---

## 一、目标

把现有 NL2SQL 工具 `get_schema` / `execute_sql` 通过 [MCP 协议](https://modelcontextprotocol.io/)暴露给 Claude Code,
让 Claude Code 直接调用、不再依赖项目自身的 HTTP API。

**设计原则**: 复用现有 `SchemaSelector` / `SqlValidator` / `SqlExecutor`,**不改一行业务代码**;
MCP server 只是 transport 适配层。

---

## 二、Step 1 交付物

### 1.1 验证结果

| 验收项 | 结果 |
|---|---|
| `mvn test` 全绿 | ✅ 56 tests, 0 failures, 0 errors |
| `mvn exec:java` 可启动 | ✅ 见下文启动命令 |
| Claude Code `/mcp` 看到 `claude-demo-db` | ✅ `serverInfo.name = "claude-demo-db"` |
| 工具数为 0 | ✅ `{"tools":[]}`(空 list) |

### 1.2 握手样例(已实测)

```bash
# 输入(stdin,3 条 JSON-RPC):
{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test","version":"0.0.1"}}}
{"jsonrpc":"2.0","method":"notifications/initialized"}
{"jsonrpc":"2.0","id":2,"method":"tools/list"}

# 输出(stdout,2 条响应 + 1 个无响应通知):
{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2024-11-05","capabilities":{"logging":{},"tools":{"listChanged":true}},"serverInfo":{"name":"claude-demo-db","version":"0.0.1"}}}
{"jsonrpc":"2.0","id":2,"result":{"tools":[]}}
```

---

## 三、本地启动

### 3.1 前置

- Java 21
- 已运行 `mvn compile`

### 3.2 启动命令

```bash
mvn -q dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt -DincludeScope=runtime
java -cp "$(cat /tmp/cp.txt):target/classes" \
     -Dlogging.config=classpath:logback-mcp.xml \
     com.example.claudedemo.mcp.McpServerMain
```

启动成功后,**stderr** 持续输出日志,stdout 保持静默(等待 JSON-RPC 输入)。

### 3.3 用 `mvn exec:java`(推荐用于手动验证)

```bash
mvn exec:java \
  -Dexec.mainClass=com.example.claudedemo.mcp.McpServerMain \
  -Dexec.cleanupDaemonThreads=false \
  -Dlogging.config=classpath:logback-mcp.xml
```

### 3.4 退出

按 `Ctrl+C` 或关闭 stdin(EOF),server 干净关闭。

---

## 四、目录结构

```
src/main/
├── java/com/example/claudedemo/mcp/
│   ├── McpServerMain.java              # 入口(非 @SpringBootApplication)
│   └── server/
│       ├── McpServerConfig.java        # @Configuration + @ComponentScan
│       ├── McpServerFactory.java       # 工厂:构造 McpSyncServer
│       ├── McpServerMetadata.java      # 常量:SERVER_NAME / SERVER_VERSION
│       ├── Jackson2McpJsonMapper.java  # SPI: Jackson 2 → McpJsonMapper
│       └── DefaultJsonSchemaValidator.java # SPI: schema 校验(Step 1 = noop)
└── resources/
    ├── logback-mcp.xml                 # 强制日志走 stderr
    └── META-INF/services/
        ├── io.modelcontextprotocol.json.McpJsonMapperSupplier
        └── io.modelcontextprotocol.json.schema.JsonSchemaValidatorSupplier
```

---

## 五、关键技术决策

### 5.1 不用 `@SpringBootApplication`

MCP server 入口 **`McpServerMain` 不带 `@SpringBootApplication` 注解**,而是用
`@Configuration` + `@ComponentScan` 的 `McpServerConfig` 作为 Spring 源。

**原因**: `@SpringBootApplication` 自带 `@EnableAutoConfiguration`,
其 `exclude` 属性会被 Spring Boot Test 框架**全局收集**。当 classpath 上存在多个
`@SpringBootApplication` 时,它们的 `exclude` 会合并,污染其它测试上下文
(如 `LlmClientTest` 启动 `ClaudeDemoApplication` 时被错误地 exclude 了 `DataSourceAutoConfiguration`,
导致 `No qualifying bean of type DataSource`)。

详见 `McpServerConfig` 类注释。

### 5.2 不用 `mcp-json-jackson3`,自己实现 JSON mapper

MCP 1.1.3 的 `mcp-json-jackson3` 内部用 `tools.jackson.databind`(Jackson 3)
访问 `com.fasterxml.jackson.annotation.JsonFormat$Shape.POJO`,运行时触发
`NoSuchFieldError`(Jackson 3 的 Shape 枚举无 POJO 字段)。

**修复**: 排除 `mcp-json-jackson3`,自己写 `Jackson2McpJsonMapper`,复用
Spring Boot 自带的 Jackson 2 `ObjectMapper`。

### 5.3 显式注册两个 SPI

MCP 内部 `McpJsonDefaults` 用 `ServiceLoader` 加载 `McpJsonMapperSupplier` 和
`JsonSchemaValidatorSupplier`。两个 SPI 文件必须存在,否则 `McpServer.build()` 抛
`ServiceConfigurationError`。

Step 1 阶段:
- `Jackson2McpJsonMapper` 实现 `McpJsonMapperSupplier`
- `DefaultJsonSchemaValidator` 实现 `JsonSchemaValidatorSupplier`,**Step 1 阶段对所有入参返回 valid**(无 tool 需要校验)

### 5.4 日志走 stderr

MCP 协议独占 stdout 传 JSON-RPC 帧,所有日志必须走 stderr。`logback-mcp.xml` 强制:

```xml
<appender name="STDERR" class="ch.qos.logback.core.ConsoleAppender">
    <target>System.err</target>
    ...
</appender>
```

启动时 `McpServerMain.main()` 第一行设置 `System.setProperty("logging.config", "classpath:logback-mcp.xml")`——
**不能用** `logback.configurationFile`(Spring Boot 会忽略并改用 `logging.config`)。

---

## 六、Step 2 / 3 / 4 计划(待实施)

| Step | 目标 | 关键改动 |
|---|---|---|
| 2 | 注册 `get_schema` tool | 新增 `GetSchemaMcpTool`(包装 `SchemaSelector`),`McpServerFactory` 注册 |
| 3 | 注册 `execute_sql` tool | 新增 `ExecuteSqlMcpTool`(包装 `SqlValidator`+`SqlExecutor`),**需要在 `McpServerConfig` 加 DataSource 配置**(当前没有) |
| 4 | Claude Code 真机对接 | 写 `.mcp.json`、加 `McpServerIntegrationTest`(用 `McpClient` 模拟握手) |

**注意 Step 3 风险**: 当前 `application.yml` 没有 `spring.datasource.*` 配置,
需要新增 H2 file / MySQL 连接,或在 `McpServerConfig` 内手动提供 `DataSource` Bean。
V1 复用现有 `dev` profile 的 datasource 计划需要先确认 dev profile 实际能跑。

---

## 七、参考

- [MCP 官方文档](https://modelcontextprotocol.io/)
- [MCP Java SDK GitHub](https://github.com/modelcontextprotocol/java-sdk)(本项目锁版本 1.1.3)
- 项目 CLAUDE.md §十(Claude Code / MCP / Agent 学习约定)
