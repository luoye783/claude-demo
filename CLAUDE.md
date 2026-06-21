# CLAUDE.md

> 本文件是 Claude Code 协作的工作准则与项目说明书。
> 项目名称：**claude-demo** ｜ 类型：Java 后端学习项目
> 目标：学习 Claude Code、MCP、Agent 的使用与开发

---

## 一、项目概述

本项目是一个**面向学习与实践**的演示项目，主要用于：

- 学习 **Claude Code** 的使用方式、最佳实践与协作流程
- 探索 **MCP（Model Context Protocol）** 服务端 / 客户端的集成方法
- 实践 **Agent（智能体）** 的设计、工具调用与多 Agent 协作

项目以企业级 Java 后端服务为载体，提供贴近真实业务（MySQL 持久化 + RocketMQ 异步消息）的环境，让 AI 协作开发有规范可依、有场景可跑。

---

## 二、技术栈

| 类别 | 选型 | 版本 |
|------|------|------|
| 开发语言 | Java | **21** |
| 构建工具 | Maven（推荐启用 Maven Wrapper） | 3.9+ |
| Web 框架 | Spring Boot | **3.x** |
| 持久层 | MyBatis | 3.x |
| 关系型数据库 | MySQL | 8.x |
| 消息队列 | RocketMQ | 5.x |
| 测试框架 | JUnit 5 + Mockito + Testcontainers | - |
| 日志框架 | SLF4J + Logback | - |

> Spring Boot 3.x 基于 Jakarta EE 9+，所有 `javax.*` 包名需替换为 `jakarta.*`。

---

## 三、目录结构（推荐）

```
claude-demo
├── src
│   ├── main
│   │   ├── java
│   │   │   └── com.example.claudedemo
│   │   │       ├── ClaudeDemoApplication.java   // 启动类
│   │   │       ├── controller/                  // 接入层：HTTP 入口
│   │   │       ├── service/                     // 业务逻辑层
│   │   │       ├── manager/                     // 复杂编排、跨服务、缓存、MQ
│   │   │       ├── mapper/                      // MyBatis DAO
│   │   │       ├── domain/                      // 持久化对象 PO
│   │   │       ├── dto/                         // 入参 DTO
│   │   │       ├── vo/                          // 出参 VO
│   │   │       ├── mq/                          // RocketMQ 生产者/消费者
│   │   │       ├── agent/                       // Agent 学习模块
│   │   │       ├── config/                      // 配置类
│   │   │       ├── exception/                   // 自定义异常
│   │   │       └── common/                      // 通用工具、常量
│   │   └── resources
│   │       ├── application.yml
│   │       ├── application-dev.yml
│   │       ├── application-prod.yml
│   │       ├── mapper/                          // MyBatis XML
│   │       ├── db/                              // SQL 脚本
│   │       └── logback-spring.xml
│   └── test
└── pom.xml
```

---

## 四、Java 编码规范

### 4.1 命名规范
- **类名**：UpperCamelCase，名词或名词短语（如 `OrderService`）
- **方法名**：lowerCamelCase，动词或动词短语（如 `queryOrder`）
- **常量**：UPPER_SNAKE_CASE
- **包名**：全小写，不使用下划线
- **布尔变量**：使用 `is / has / can` 前缀（如 `isValid`）
- **PO/DTO/VO 命名**：`UserPO`、`UserDTO`、`UserVO`，类型后缀必须显式标注

### 4.2 注释与文档
- 所有 `public` 类与 `public` 方法必须有 **Javadoc**
- 关键业务逻辑必须添加行内注释
- TODO 必须署名：`// TODO(zhangsan): 说明`
- **优先中文**；技术专有名词保留英文
- 类注释必须包含：作者、创建日期、用途

### 4.3 代码风格
- 缩进：**4 个空格**（不使用 Tab）
- 行长度：不超过 **120 字符**
- 单文件不超过 **800 行**，超出请拆分
- 单方法不超过 **80 行**，超出请抽取私有方法
- 禁止 `System.out.println`，统一使用 SLF4J
- 禁止「魔法值」，常量必须定义后引用
- 使用 `Optional` 替代 `null` 返回，对外接口除外

---

## 五、Spring Boot 工程规范

### 5.1 分层职责

| 层级 | 职责 | 禁止事项 |
|------|------|----------|
| Controller | 参数校验、调用 Service、统一返回 | 不写业务逻辑、不直接访问数据库 |
| Service | 核心业务逻辑、事务管理 | 不处理 HTTP 相关对象 |
| Manager | 跨服务调用、复杂编排、缓存、MQ 发送 | 不直接接收前端参数 |
| Mapper | 数据库访问 | 不写业务逻辑 |

### 5.2 统一响应格式
```json
{
  "code": 0,
  "message": "ok",
  "data": { ... },
  "traceId": "abc123"
}
```
- `code = 0` 表示成功；非 0 表示业务异常
- 通过 `@RestControllerAdvice` 统一封装
- 异常响应 `code` 与 `message` 需对外可读、可定位

### 5.3 异常处理
- 自定义业务异常继承 `BusinessException`
- 使用 `@RestControllerAdvice` + `@ExceptionHandler` 全局捕获
- 异常信息**必须脱敏**，禁止直接返回 SQL / 堆栈到前端
- 业务校验失败抛出 `BusinessException`，不返回 `null`

### 5.4 参数校验
- 使用 `jakarta.validation`（JSR 380）注解
- Controller 入参必须 `@Valid`
- 校验失败由全局异常处理器统一返回

### 5.5 日志规范
- 使用 SLF4J，**禁止** `System.out` / `e.printStackTrace()`
- 关键节点（入口、外部调用、出参、异常）必须打印日志
- 日志格式：`时间 [线程] 级别 Logger - traceId|spanId | 消息`
- 生产环境开启 MDC traceId，便于链路追踪

---

## 六、MyBatis 规范

- Mapper 接口与 XML 命名保持一致
- 复杂 SQL 写在 XML；简单 SQL 可用注解
- **禁止** `SELECT *`，必须显式列出字段
- 批量操作使用 `foreach`，**单批不超过 1000 条**
- 分页统一使用 **PageHelper**（或 MyBatis-Plus 分页插件）
- 启用 `map-underscore-to-camel-case`，PO 字段驼峰即可
- 重要 SQL 在 XML 顶部加注释说明业务用途
- 复杂查询的列别名需与 PO 字段一一对应

---

## 七、MySQL 规范

- 库名 / 表名 / 字段名：**小写 + 下划线**
- 表必须有主键：`id BIGINT AUTO_INCREMENT`
- 必备字段：
  - `create_time DATETIME DEFAULT CURRENT_TIMESTAMP`
  - `update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP`
  - `is_deleted TINYINT DEFAULT 0`（逻辑删除）
- 字符集：`utf8mb4`，排序规则：`utf8mb4_unicode_ci`
- 索引命名：`idx_字段名`、`uniq_字段名`
- 单表超 **500 万**行需评估分表
- DDL 必须审核，**禁止**在生产直接执行

---

## 八、RocketMQ 规范

- **Topic 命名**：`业务域_业务动作`，例：`order_created`、`trade_paid`
- **Tag 命名**：业务子类型，例：`vip`、`normal`
- **生产者**：
  - 必须捕获发送结果（同步 / 异步 / oneway 视业务而定）
  - 失败必须有重试或降级策略
- **消费者**：
  - **必须幂等**（基于业务唯一键去重）
  - 必须支持重试与**死信队列**
  - 消费失败先重试 3 次，再进死信
  - 禁止在消费方法内做长事务
- 消息体使用 **JSON**，禁止直接传对象
- 关键业务消息需记录 `messageId`、`key`、链路 `traceId`

---

## 九、Git 提交规范

使用 **Conventional Commits**：

```
<type>(<scope>): <subject>

<body>

<footer>
```

| type | 说明 |
|------|------|
| feat | 新功能 |
| fix | 修复 Bug |
| refactor | 重构（无功能变化） |
| docs | 文档变更 |
| style | 格式调整（无逻辑变化） |
| test | 测试相关 |
| chore | 构建 / 工具 / 依赖变更 |
| perf | 性能优化 |

**禁止**直接 push 到 `main` / `master`，必须通过 Pull Request。

---

## 十、Claude Code / MCP / Agent 学习约定

> 本项目也是 AI 协作的练兵场，所有 AI 行为准则优先于本文件之外的个人习惯。

### 10.1 Claude Code 使用准则
- 每次重要操作前先 `Read` 相关文件，**避免盲改**
- 复杂任务优先使用 `Plan` 模式与用户对齐方案
- 涉及删除、重写、外部发送等**不可逆操作**必须先确认
- 输出代码时用 `file_path:line_number` 引用上下文
- 注释与文档**优先使用中文**

### 10.2 MCP 集成
- MCP 工具的 `name` 与 `description` 必须**清晰可枚举**
- 工具命名遵循 `verb_noun` 模式，例：`query_user`、`send_email`
- 任何**危险操作**（写库、发消息、删文件）需在 `description` 中明示风险
- MCP 服务器配置优先放项目根目录的 `.mcp.json`
- 服务端实现建议放在 `agent/mcp/` 包下

### 10.3 Agent 设计
- 单一 Agent 单一职责，复杂场景拆分为多 Agent 协作
- 工具调用必须**捕获异常**并降级
- 决策日志必须**可追溯**（记录 prompt、工具调用、结果）
- 学习示例存放在 `agent/` 包下，每个示例附 README
- Agent 与传统 Service 的边界：Agent 处理"非确定性决策"，Service 处理"确定性业务"

---

## 十一、常用命令

> 后续将根据项目实际构建脚本补充。

```bash
# 编译
./mvnw clean compile

# 本地启动（dev profile）
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# 打包（跳过测试）
./mvnw clean package -DskipTests

# 运行测试
./mvnw test

# 代码格式化
./mvnw spotless:apply
```

---

## 十二、变更记录

| 日期 | 版本 | 变更人 | 说明 |
|------|------|--------|------|
| 2026-06-10 | 0.0.1 | claude-code | 项目初始化，创建 CLAUDE.md |
