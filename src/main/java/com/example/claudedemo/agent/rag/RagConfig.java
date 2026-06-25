package com.example.claudedemo.agent.rag;

import com.example.claudedemo.agent.rag.chunker.SimpleTextChunker;
import com.example.claudedemo.agent.rag.embedding.SimpleHashEmbeddingClient;
import com.example.claudedemo.agent.rag.loader.MarkdownKnowledgeDocumentLoader;
import com.example.claudedemo.agent.rag.store.InMemoryVectorStore;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.ResourcePatternResolver;

/**
 * RAG 模块 Spring 装配(V2 第八阶段 RAG V3).
 *
 * <p>装配:
 * <ul>
 *   <li>{@link MarkdownKnowledgeDocumentLoader} — 从 knowledge-base 目录加载文档</li>
 *   <li>{@link SimpleTextChunker} — 按字符长度切分文档</li>
 *   <li>{@link SimpleHashEmbeddingClient} — V3 哈希 embedding(仅演示)</li>
 *   <li>{@link InMemoryVectorStore} — V3 内存向量存储</li>
 *   <li>{@link InMemoryRagRetriever} — 支持 keyword / vector 双路径</li>
 * </ul>
 *
 * <p>{@link RagRetriever} 通过 {@code @Bean} 暴露,供 {@code Nl2SqlMcpAgent} 的
 * {@code ObjectProvider} 注入。未装配时降级为"无 RAG"路径。
 *
 * @since 0.0.1
 */
@Configuration
@EnableConfigurationProperties(RagProperties.class)
public class RagConfig {

    @Bean
    public MarkdownKnowledgeDocumentLoader markdownLoader(RagProperties props,
                                                          ResourcePatternResolver resolver) {
        return new MarkdownKnowledgeDocumentLoader(props.getKnowledgeBasePath(), resolver);
    }

    @Bean
    public SimpleTextChunker simpleTextChunker(RagProperties props) {
        return new SimpleTextChunker(props.getChunkSize(), props.getChunkOverlap());
    }

    @Bean
    public SimpleHashEmbeddingClient simpleHashEmbeddingClient(RagProperties props) {
        return new SimpleHashEmbeddingClient(props);
    }

    @Bean
    public InMemoryVectorStore inMemoryVectorStore() {
        return new InMemoryVectorStore();
    }

    /**
     * V3 完整检索器:根据 {@code props.retrievalMode} 决定是否启用向量路径.
     */
    @Bean
    public RagRetriever inMemoryRagRetriever(MarkdownKnowledgeDocumentLoader loader,
                                              SimpleTextChunker chunker,
                                              RagProperties props,
                                              SimpleHashEmbeddingClient embeddingClient,
                                              InMemoryVectorStore vectorStore) {
        return new InMemoryRagRetriever(loader, chunker, props, embeddingClient, vectorStore);
    }
}
