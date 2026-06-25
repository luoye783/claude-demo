package com.example.claudedemo.agent.rag;

import java.util.List;

/**
 * RAG 检索器接口(V2 第六阶段 RAG V1).
 *
 * <p>由 {@link com.example.claudedemo.agent.mcp.Nl2SqlMcpAgent} 在
 * 拼装 LLM messages 前调用,基于 question 召回相关业务知识文档。
 *
 * <p><b>与 MCP 工具的关系</b>:
 * RAG 是"<i>上下文增强</i>"(Agent 隐式注入),不暴露为 LLM tool_choice;
 * MCP 工具是"<i>结构化操作</i>"(LLM 显式调用),两者互不替代。
 *
 * <p><b>实现选择</b>:
 * <ul>
 *   <li>V1: {@link InMemoryRagRetriever}(内置文档 + 关键词匹配)</li>
 *   <li>V2: 基于 Embedding / 向量库 / 混合检索 的实现(只换实现,接口不变)</li>
 * </ul>
 *
 * <p><b>失败策略</b>:实现类应将异常吞掉并返回空列表,不抛给 Agent;
 * Agent 侧会做二次保护(try/catch + log warn),不阻断主链路。
 *
 * @since 0.0.1
 */
public interface RagRetriever {

    /**
     * 基于问题检索相关知识文档.
     *
     * @param question 用户问题(非空)
     * @param topK     最大返回文档数,必须 &gt; 0
     * @return 按 score 降序的文档列表;无命中时返回空列表
     * @throws IllegalArgumentException 当 question 为 null/blank 或 topK &lt;= 0
     */
    List<RagDocument> retrieve(String question, int topK);
}
