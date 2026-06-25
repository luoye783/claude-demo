# RAG 接入点分析

> 第六阶段 RAG（Retrieval-Augmented Generation）的接入点规划。
> **核心问题**：RAG 应该接在哪个子系统的哪个边界上？

---

## 一、TL;DR — 推荐方案

**推荐**：

> **RAG 接在 Runtime 与 LLM 之间，作为"消息拼装"的一个新阶段。**
> 物理位置：`agent/rag/RagRetriever`（新增）→ `Nl2SqlMcpAgent.buildInitialMessages` 内部调用。
> 不动 MCP / Tool / Memory 子系统。

**为什么是这个位置**：
1. **RAG 的输出是 context 文本**，与"历史摘要"在 messages 拼装时地位相同 —— 都是 system 消息。
2. **RAG 检索的"知识库"通常与 MCP 工具无关**（业务文档 / 产品手册 / FAQ），不应该污染工具层。
3. **复用 `buildInitialMessages` 的扩展性**：在 system 与 turns 之间插入 `## 检索到的相关知识` 段，与 summary 段并排。
4. **不破坏五大子系统边界**：RAG 作为 Runtime 内部的可选阶段，新增 `agent.rag` 包。

---

## 二、三个候选接入点

### 候选 A：在 Runtime 内 messages 拼装阶段插入（✅ 推荐）

**位置**：`Nl2SqlMcpAgent.buildInitialMessages` 中,在 system prompt 后插入检索结果。

```
messages:
  [0] system:        SYSTEM_PROMPT
  [1] system:        ## 检索到的相关知识 ...    ← 新增 RAG
  [2] system:        ## 历史摘要 + 关键事实     (仅 memory.hasSummary)
  [3..n] user/assistant: history
  [n+1] user:        question
```

**新增组件**：
```
agent/rag/
├── RagRetriever.java          // 接口
├── KnowledgeBaseRetriever.java // 默认实现（基于本地文件 / MySQL 全文索引 / 后续可换向量库）
├── RagProperties.java         // 开关 + 召回 topK + 相似度阈值
└── RagConfig.java             // 装配
```

**改动面**：
- `Nl2SqlMcpAgent` 构造器注入 `RagRetriever`(可选)
- `buildInitialMessages` 多一段
- 老测试零修改(可注入 null)
- 边界保持:Runtime 内部扩展

**优点**:
- 边界清晰,RAG 是 Runtime 的子能力
- 与 Memory 摘要并列,semantic 上一致("压缩过去" vs "检索外部")
- 不影响 Tool / MCP / LLM 子系统
- 后续升级为向量库只需换 RagRetriever 实现

**缺点**:
- RAG 调用与 LLM 调用串行(每轮循环前检索),增加延迟
- 如果 RAG 结果进入 messages 后 LLM 仍答错,只能通过调 prompt 解决

---

### 候选 B：把 RAG 包装成 MCP Tool

**位置**：在 MCP Server 注册 `search_knowledge` / `search_docs` 工具,LLM 自主决定何时调用。

```
LLM 看到 tools = [get_schema, execute_sql, search_knowledge]
LLM 自主决定: "我需要先 search_knowledge 再 get_schema"
```

**优点**:
- 复用 MCP 工具机制,无需新加 Runtime 概念
- LLM 自主决定何时需要外部知识

**缺点**:
- **污染 Tool 层**:知识库检索本质上不是"工具",而是"context 来源"
- 工具调用要走 STDIO 子进程,RAG 查询走 HTTP/MySQL/向量库,跨进程不合理
- LLM 不一定知道何时该用(没有 summary 的"自动背景"语义)
- 每次调用都增加 tool message,挤占上下文窗口
- **不符合 CLAUDE.md §10.3 "Agent 与 Service 边界"**:RAG 检索是确定性的,应该作为 Service 而不是 Agent tool

> **不推荐**:违背解耦原则,性能差,语义错位。

---

### 候选 C：把 RAG 接在 Memory 上(扩展 SummaryMemory)

**位置**：把 RAG 检索结果并入 `SummaryMemory` 一起注入 messages。

```
messages:
  [system: SYSTEM_PROMPT]
  [system: ## 历史摘要 + ## 关键事实 + ## 相关知识]  ← 合并
  ...
```

**优点**:
- 复用 Memory 注入点,改动小
- "summary + 知识" 看起来都是"过去 + 外部"的背景

**缺点**:
- **耦合严重**:RAG 不属于"短期会话记忆",与 ConversationTurn 无关
- `SummaryMemory` 是"对过去的压缩",RAG 是"对外部的检索",语义不同
- 每次进新 turn 都要重算 RAG,但 summary 可能很久才更新,生命周期不一致
- `MemoryCompressor` prompt 也要改,增加耦合

> **不推荐**:破坏 Memory 子系统的"会话状态"语义。

---

## 三、推荐方案详细设计

### 3.1 新增包结构

```
agent/rag/
├── RagRetriever.java              // 接口:retrieve(question) -> List<Chunk>
├── KnowledgeBaseRetriever.java    // V1 默认实现:本地文件 + 关键词匹配
├── RetrievedChunk.java            // record: content + score + source
├── RagProperties.java             // @ConfigurationProperties
└── RagConfig.java                 // @Configuration
```

### 3.2 `RagRetriever` 接口

```java
public interface RagRetriever {
    /**
     * 基于问题检索相关知识块
     * @return 召回的知识块列表(按相关度降序);为空表示"无相关知识"
     */
    List<RetrievedChunk> retrieve(String question, int topK);
}
```

### 3.3 `Nl2SqlMcpAgent.buildInitialMessages` 改造点

```java
private List<ChatMessage> buildInitialMessages(AgentSession session, String question) {
    List<ChatMessage> messages = new ArrayList<>();
    messages.add(new ChatMessage("system", SYSTEM_PROMPT));
    
    // 新增 RAG 注入(在 summary 之前)
    if (ragRetriever != null) {
        List<RetrievedChunk> chunks = ragRetriever.retrieve(question, ragProps.getTopK());
        if (!chunks.isEmpty()) {
            messages.add(new ChatMessage("system", buildRagMessage(chunks)));
        }
    }
    
    if (session.hasMemory()) {
        // 原有 summary + turns
    }
    messages.add(new ChatMessage("user", question));
    return messages;
}

private String buildRagMessage(List<RetrievedChunk> chunks) {
    StringBuilder sb = new StringBuilder("## 检索到的相关知识\n");
    for (RetrievedChunk c : chunks) {
        sb.append("- ").append(c.content()).append("\n");
    }
    return sb.toString();
}
```

### 3.4 关键不变量

1. **RAG 失败不阻断主链路**:`ragRetriever.retrieve` 抛异常时,`buildRagMessage` 返回空字符串,Agent 继续。
2. **RAG 不进入 trace**:检索步骤不写入 `AgentTrace`(节省空间,避免污染),或单独建一个 `RAG_STEP` 类型。
3. **RAG 检索结果不写回 memory**:知识是外部静态信息,不属于会话状态。
4. **`topK` 限制**:防止一次性塞太多 context,挤占 turns 空间。
5. **可选依赖**:`ragRetriever` 注入为 null 时,与无 RAG 行为一致 —— 老测试零修改。

### 3.5 测试矩阵

| 测试 | 验证点 |
|------|--------|
| `RagRetriever` 单元测试 | 接口契约、null/empty 输入 |
| `KnowledgeBaseRetriever` 单元测试 | 关键词匹配召回 |
| `Nl2SqlMcpAgent` 集成测试 | RAG 注入 messages 的位置 / 内容 / 顺序;无 RAG 时行为不变 |
| 失败降级 | ragRetriever 抛异常时,Agent 继续走原链路 |
| 边界 | RAG 检索结果不进 trace;不进 memory |

---

## 四、未来升级路径

| 阶段 | 升级 |
|------|------|
| V2 第六阶段 RAG V1 | `KnowledgeBaseRetriever`(本地文件 / MySQL `MATCH AGAINST`) |
| V2 第七阶段 RAG V2 | 引入 Embedding + 向量库(Redis Stack / Chroma / Milvus),只换实现,接口不变 |
| V2 第八阶段 RAG V3 | 混合检索(BM25 + 向量),rerank,query 改写 |

**关键**：所有升级都在 `agent/rag/` 包内完成,不动 Runtime / Memory / MCP / LLM / Tool 任何一个。

---

## 五、对比表

| 维度 | 候选 A (Runtime 内) | 候选 B (MCP Tool) | 候选 C (Memory) |
|------|---------------------|--------------------|-----------------|
| 子系统边界 | ✅ 保持 | ❌ 污染 Tool | ❌ 污染 Memory |
| 性能 | 中(串行) | 差(每轮跨进程) | 好(与 summary 合并) |
| LLM 自主性 | 中(自动注入) | 高(LLM 决定) | 中 |
| 实现复杂度 | 中 | 低(复用 MCP) | 中 |
| 升级到向量库 | 易(换实现) | 难(改协议) | 易 |
| 语义正确性 | ✅ context 来源 | ❌ 工具调用 | ❌ 混语义 |
| **推荐** | ✅ | ❌ | ❌ |
