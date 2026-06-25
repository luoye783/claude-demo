package com.example.claudedemo.agent.rag;

import java.util.Collections;
import java.util.Map;

/**
 * 原始知识文档(V2 第七阶段 RAG V2).
 *
 * <p>由 {@link KnowledgeDocumentLoader} 从外部源(knowledge-base 目录)加载,
 * 保留文档的原始结构;与 {@link RagDocument} 不同——本类不含检索相关字段
 * (score / keywords),仅为下游 {@link TextChunker} 提供原始材料。
 *
 * @param id      文档唯一 ID(通常为文件名去扩展名)
 * @param title   文档标题(第一行 # title)
 * @param content 全文内容
 * @param source  来源路径(如 "knowledge-base/users.md")
 * @param metadata 扩展元信息(路径、修改时间等)
 * @since 0.0.1
 */
public record KnowledgeDocument(
        String id,
        String title,
        String content,
        String source,
        Map<String, Object> metadata
) {

    public KnowledgeDocument {
        metadata = (metadata == null) ? Map.of() : Map.copyOf(metadata);
    }

    public Map<String, Object> metadataView() {
        return Collections.unmodifiableMap(metadata);
    }
}
