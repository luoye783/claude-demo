package com.example.claudedemo.agent.rag.store;

import com.example.claudedemo.agent.rag.embedding.EmbeddingVector;

import java.util.Collections;
import java.util.Map;

/**
 * 向量文档(V2 第十一阶段 RAG V6).
 *
 * <p>由 {@link VectorStore} 存储与检索的单元,包含原始文本与对应的嵌入向量。
 *
 * <p><b>V6 新增字段</b>:
 * <ul>
 *   <li>{@link #documentId} — 所属文档 ID,用于 {@code deleteByDocumentId}</li>
 *   <li>{@link #chunkHash} — chunk 内容 SHA-256 指纹,用于增量索引判断</li>
 * </ul>
 *
 * @param id          唯一标识
 * @param documentId  所属文档 ID(null 时从 id 自动提取)
 * @param content     文本内容
 * @param source      来源路径
 * @param chunkHash   chunk 内容指纹(SHA-256 hex),无则为 null
 * @param metadata    元数据
 * @param embedding   嵌入向量
 * @since 0.0.1
 */
public record VectorDocument(
        String id,
        String documentId,
        String content,
        String source,
        String chunkHash,
        Map<String, Object> metadata,
        EmbeddingVector embedding
) {

    public VectorDocument {
        metadata = (metadata == null) ? Map.of() : Map.copyOf(metadata);
        if (documentId == null || documentId.isBlank()) {
            documentId = extractDocumentId(id);
        }
    }

    /**
     * V3 兼容构造器:无 documentId/chunkHash.
     * documentId 从 id 自动提取,chunkHash 为 null.
     */
    public VectorDocument(String id, String content, String source,
                          Map<String, Object> metadata, EmbeddingVector embedding) {
        this(id, null, content, source, null, metadata, embedding);
    }

    public Map<String, Object> metadataView() {
        return Collections.unmodifiableMap(metadata);
    }

    /**
     * 从 chunk id 中提取 documentId.
     *
     * <p>规则:id 格式为 {@code {documentId}-chunk-{index}} 时提取前缀;
     * 否则返回 id 本身。
     */
    static String extractDocumentId(String id) {
        if (id == null) return null;
        int idx = id.lastIndexOf("-chunk-");
        return idx > 0 ? id.substring(0, idx) : id;
    }
}
