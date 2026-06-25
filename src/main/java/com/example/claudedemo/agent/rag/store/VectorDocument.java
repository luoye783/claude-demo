package com.example.claudedemo.agent.rag.store;

import com.example.claudedemo.agent.rag.embedding.EmbeddingVector;

import java.util.Collections;
import java.util.Map;

/**
 * 向量文档(V2 第八阶段 RAG V3).
 *
 * <p>由 {@link VectorStore} 存储与检索的单元,包含原始文本与对应的嵌入向量。
 *
 * @param id        唯一标识
 * @param content   文本内容
 * @param source    来源路径
 * @param metadata  元数据
 * @param embedding 嵌入向量
 * @since 0.0.1
 */
public record VectorDocument(
        String id,
        String content,
        String source,
        Map<String, Object> metadata,
        EmbeddingVector embedding
) {

    public VectorDocument {
        metadata = (metadata == null) ? Map.of() : Map.copyOf(metadata);
    }

    public Map<String, Object> metadataView() {
        return Collections.unmodifiableMap(metadata);
    }
}
