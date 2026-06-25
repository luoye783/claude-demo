package com.example.claudedemo.agent.rag;

import java.util.Collections;
import java.util.Map;

/**
 * 知识文档切分后的最小检索单元(V2 第七阶段 RAG V2).
 *
 * <p>由 {@link TextChunker#chunk(KnowledgeDocument)} 对原始文档切分产出;
 * 是 {@link InMemoryRagRetriever} 的检索单位。每条 chunk 可独立参与关键词匹配,
 * 匹配命中后转换为 {@link RagDocument} 返回给 Agent。
 *
 * @param id           chunk 唯一 ID(如 "users-chunk-0")
 * @param documentId   所属原始文档 ID
 * @param title        文档标题(所有 chunk 共用)
 * @param content      chunk 文本片段
 * @param source       来源路径
 * @param chunkIndex   原文中的顺序(0-based)
 * @param metadata     扩展元信息
 * @since 0.0.1
 */
public record KnowledgeChunk(
        String id,
        String documentId,
        String title,
        String content,
        String source,
        int chunkIndex,
        Map<String, Object> metadata
) {

    public KnowledgeChunk {
        metadata = (metadata == null) ? Map.of() : Map.copyOf(metadata);
    }

    public Map<String, Object> metadataView() {
        return Collections.unmodifiableMap(metadata);
    }
}
