package com.example.claudedemo.agent.rag.store;

import com.example.claudedemo.agent.rag.embedding.EmbeddingVector;
import com.example.claudedemo.agent.rag.RagRetriever;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 向量存储与检索接口(V2 第十一阶段 RAG V6).
 *
 * <p>存储 {@link VectorDocument} 并在构造时(或运行时)接受 {@link EmbeddingVector}
 * 查询,返回按相似度降序排列的结果。
 *
 * <p>V1 实现为 {@link InMemoryVectorStore}(暴力搜索),后续可换 pgvector / Milvus 等。
 *
 * <p><b>使用方</b>:{@link RagRetriever} 的实现类在检索时调用 {@link #search},
 * Agent 不感知本接口。
 *
 * <p><b>V6 新增索引生命周期方法</b>:
 * {@link #clear} / {@link #deleteByDocumentId} / {@link #count} /
 * {@link #exists} / {@link #findById} / {@link #getDocumentIds}
 *
 * @since 0.0.1
 */
public interface VectorStore {

    // ==================== 写入 ====================

    /**
     * 插入或更新文档(id 相同时覆盖).
     */
    void upsert(VectorDocument document);

    /**
     * 批量插入或更新.
     */
    default void upsertAll(List<VectorDocument> documents) {
        if (documents == null) return;
        for (VectorDocument doc : documents) {
            upsert(doc);
        }
    }

    // ==================== 检索 ====================

    /**
     * 搜索与 queryVector 最相似的 topK 条结果.
     *
     * @param queryVector 查询向量(由 {@link com.example.claudedemo.agent.rag.embedding.EmbeddingClient#embed} 生成)
     * @param topK        返回结果数上限
     * @return 按 score 降序的结果列表;无匹配时返回空列表
     */
    List<VectorSearchResult> search(EmbeddingVector queryVector, int topK);

    // ==================== 索引生命周期(V6 新增) ====================

    /**
     * 清空所有向量.
     */
    void clear();

    /**
     * 删除指定文档的所有 chunk.
     *
     * @param documentId 文档 ID
     */
    void deleteByDocumentId(String documentId);

    /**
     * 向量总数.
     */
    long count();

    /**
     * 指定 chunk 是否存在.
     */
    boolean exists(String id);

    /**
     * 按 chunk id 查找 VectorDocument(含 chunkHash 用于增量判断).
     */
    Optional<VectorDocument> findById(String id);

    /**
     * 获取已索引的 documentId 集合(去重).
     */
    Set<String> getDocumentIds();
}
