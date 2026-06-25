# Claude Demo — 架构总览

> 截至 **Agent Runtime V2 第五阶段 AgentSession** 完成时的项目架构快照。
> 本目录是"读代码前的地图"，不是 API 文档。

---

## 一、TL;DR

claude-demo 是一个**企业级 Java 后端 + Agent 演示项目**：

- **业务底盘**：MyBatis + MySQL + RocketMQ 的标准 Spring Boot 服务（V1 完成）。
- **Agent Runtime V2**：围绕 `Nl2SqlMcpAgent` 构建的 NL2SQL Agent，已完成 5 个阶段。
  - 第一阶段 Tool Calling（固定两轮 SQL 生成）
  - 第二阶段 MCP 集成（解耦工具定义 / 执行）
  - 第三阶段 Memory V1（短期会话记忆 + FIFO 裁剪）
  - 第四阶段 Summary Memory（基于 turn 数触发的 LLM 摘要压缩）
  - 第五阶段 AgentSession（运行时上下文统一对象）
- **下一步**：第六阶段 RAG（向量检索 / 外部知识库）—— 接入点见 [`architecture/rag-integration-point.md`](architecture/rag-integration-point.md)。

---

## 二、文档目录

| 文件 | 内容 |
|------|------|
| [`architecture/modules.md`](architecture/modules.md) | 模块划分、依赖方向、Spring 装配点 |
| [`architecture/call-chain.md`](architecture/call-chain.md) | NL2SQL Agent 端到端调用链 + 时序图 |
| [`architecture/boundaries.md`](architecture/boundaries.md) | Runtime / Memory / MCP / LLM / Tool 五大子系统边界 |
| [`architecture/rag-integration-point.md`](architecture/rag-integration-point.md) | RAG 接入点分析（候选 + 推荐 + 不推荐） |
| [`diagrams/README.md`](diagrams/README.md) | 图表索引（Mermaid 源 + 渲染说明） |

---

## 三、一图看全局

> 五个子系统 + 一个主链路 + Memory/Trace 在主链路外圈。
> 详细解读见 [`architecture/boundaries.md`](architecture/boundaries.md)。

![Global Architecture](diagrams/global-architecture.svg)

> Mermaid 源在 [`diagrams/global-architecture.mmd`](diagrams/global-architecture.mmd)；
> 渲染为 SVG 用 `mmdc -i diagrams/global-architecture.mmd -o diagrams/global-architecture.svg`（需 `@mermaid-js/mermaid-cli`）。

---

## 四、版本基线

| 项 | 值 |
|----|----|
| Java | 21 |
| Spring Boot | 3.3.5 |
| MCP SDK | 1.1.3（pom 锁定） |
| LLM 协议 | OpenAI Chat Completions（火山 Ark） |
| 状态持久化 | MySQL 8（业务） / `ConcurrentHashMap`（Memory） |
| 测试 | JUnit 5 + Mockito；**213 个测试全绿** |
| Agent Runtime V2 阶段 | 1 ✅ 2 ✅ 3 ✅ 4 ✅ 5 ✅ / 6 RAG 规划中 |

---

## 五、阅读顺序建议

1. 第一次来：先看 [`architecture/modules.md`](architecture/modules.md) — 知道有什么。
2. 想跑通链路：看 [`architecture/call-chain.md`](architecture/call-chain.md) — 知道一次请求怎么流。
3. 想改 / 扩展：看 [`architecture/boundaries.md`](architecture/boundaries.md) — 知道哪里能改、哪里不能改。
4. 想做 RAG：直接看 [`architecture/rag-integration-point.md`](architecture/rag-integration-point.md)。
