package com.example.claudedemo.agent.rag;

import java.util.List;

/**
 * 文本切分器接口(V2 第七阶段 RAG V2).
 *
 * <p>将原始 {@link KnowledgeDocument} 切分为 {@link KnowledgeChunk} 列表,
 * 供 {@link InMemoryRagRetriever} 构建检索索引。
 *
 * <p><b>V1 实现</b>:{@link com.example.claudedemo.agent.rag.chunker.SimpleTextChunker}
 * ——基于字符长度的简单切分,不做语义分析。
 *
 * @since 0.0.1
 */
public interface TextChunker {

    /**
     * 将文档切分为 chunk.
     *
     * @param document 原始文档,不可为 null
     * @return chunk 列表;文档内容为空或长度为零时返回空列表
     */
    List<KnowledgeChunk> chunk(KnowledgeDocument document);
}
