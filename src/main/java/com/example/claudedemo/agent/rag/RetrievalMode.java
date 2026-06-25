package com.example.claudedemo.agent.rag;

/**
 * RAG 检索模式(V2 第八阶段 RAG V3).
 *
 * <p>控制 {@link InMemoryRagRetriever} 内部使用的检索算法。
 * 构造时确定,运行期内不可变;如需切换需重启。
 *
 * <ul>
 *   <li>{@link #KEYWORD} — 基于 token 匹配的关键词检索(V2 默认)</li>
 *   <li>{@link #VECTOR}  — 基于 {@link com.example.claudedemo.agent.rag.embedding.EmbeddingClient}
 *       的向量检索(V3 新增)</li>
 * </ul>
 *
 * @since 0.0.1
 */
public enum RetrievalMode {
    KEYWORD,
    VECTOR
}
