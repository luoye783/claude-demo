package com.example.claudedemo.agent.rag;

import java.util.List;

/**
 * 知识文档加载器接口(V2 第七阶段 RAG V2).
 *
 * <p>从配置的路径(文件系统 / classpath)加载 {@link KnowledgeDocument} 列表,
 * 由 {@link TextChunker} 切分后供 {@link InMemoryRagRetriever} 检索。
 *
 * <p><b>失败策略</b>:路径不存在或读取失败时返回空列表,不抛异常。
 * 调用方(如 {@link InMemoryRagRetriever} 构造器)将获得空语料。
 *
 * @since 0.0.1
 */
public interface KnowledgeDocumentLoader {

    /**
     * 加载所有知识文档.
     *
     * @return 文档列表;失败或无文档时返回空列表
     */
    List<KnowledgeDocument> load();
}
