package com.example.claudedemo.agent.rag.store;

/**
 * 向量检索结果(V2 第八阶段 RAG V3).
 *
 * <p>由 {@link VectorStore#search} 返回,包含命中的 {@link VectorDocument} 与相似度分数。
 *
 * @param document 命中的文档
 * @param score    余弦相似度(-1~1),V3 钳位到 [0,1] 以兼容 {@link com.example.claudedemo.agent.rag.RagDocument#score()}
 * @since 0.0.1
 */
public record VectorSearchResult(
        VectorDocument document,
        double score
) {
}
