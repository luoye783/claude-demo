package com.example.claudedemo.agent.rag;

import com.example.claudedemo.agent.rag.chunker.SimpleTextChunker;
import com.example.claudedemo.agent.rag.embedding.EmbeddingClient;
import com.example.claudedemo.agent.rag.loader.MarkdownKnowledgeDocumentLoader;
import com.example.claudedemo.agent.rag.store.InMemoryVectorStore;
import com.example.claudedemo.agent.rag.store.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.ResourcePatternResolver;

/**
 * RAG 检索模块 Spring 装配(V2 第十阶段 RAG V5).
 *
 * <p>装配:
 * <ul>
 *   <li>{@link MarkdownKnowledgeDocumentLoader}</li>
 *   <li>{@link SimpleTextChunker}</li>
 *   <li>{@link InMemoryVectorStore} — 默认,用于本地/测试</li>
 *   <li>{@link InMemoryRagRetriever} — keyword + vector 双路径</li>
 * </ul>
 *
 * <p><b>说明</b>:{@link VectorStore} 由本配置类提供默认的 {@link InMemoryVectorStore};
 * 当 {@code vector-store.provider=pgvector} 时,由
 * {@link com.example.claudedemo.agent.rag.store.PgVectorStoreConfig} 接管并覆盖
 * {@code VectorStore} bean。{@code RagConfig} 不感知具体 provider 实现。
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

    /**
     * 默认的内存向量存储(provider=in-memory 时使用).
     *
     * <p>当 provider=pgvector 时,{@code PgVectorStoreConfig} 会创建同名的
     * {@code VectorStore} bean,此 bean 被跳过,不生效。
     */
    @Bean
    @ConditionalOnProperty(name = "vector-store.provider", havingValue = "in-memory", matchIfMissing = true)
    public VectorStore inMemoryVectorStore() {
        return new InMemoryVectorStore();
    }

    @Bean
    public RagRetriever inMemoryRagRetriever(MarkdownKnowledgeDocumentLoader loader,
                                              SimpleTextChunker chunker,
                                              RagProperties props,
                                              EmbeddingClient embeddingClient,
                                              VectorStore vectorStore) {
        return new InMemoryRagRetriever(loader, chunker, props, embeddingClient, vectorStore);
    }
}
