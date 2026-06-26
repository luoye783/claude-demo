package com.example.claudedemo.agent.rag;

import com.example.claudedemo.agent.rag.chunker.SimpleTextChunker;
import com.example.claudedemo.agent.rag.embedding.EmbeddingClient;
import com.example.claudedemo.agent.rag.loader.MarkdownKnowledgeDocumentLoader;
import com.example.claudedemo.agent.rag.store.InMemoryVectorStore;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.ResourcePatternResolver;

/**
 * RAG 检索模块 Spring 装配(V2 第九阶段 RAG V4).
 *
 * <p>装配:
 * <ul>
 *   <li>{@link MarkdownKnowledgeDocumentLoader}</li>
 *   <li>{@link SimpleTextChunker}</li>
 *   <li>{@link InMemoryVectorStore}</li>
 *   <li>{@link InMemoryRagRetriever}(keyword + vector 双路径)</li>
 * </ul>
 *
 * <p><b>说明</b>:{@link EmbeddingClient} 由 {@link com.example.claudedemo.agent.rag.embedding.EmbeddingConfig}
 * 根据 provider 选择创建,本配置类仅注入使用。
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
    public InMemoryVectorStore inMemoryVectorStore() {
        return new InMemoryVectorStore();
    }

    @Bean
    public RagRetriever inMemoryRagRetriever(MarkdownKnowledgeDocumentLoader loader,
                                              SimpleTextChunker chunker,
                                              RagProperties props,
                                              EmbeddingClient embeddingClient,
                                              InMemoryVectorStore vectorStore) {
        return new InMemoryRagRetriever(loader, chunker, props, embeddingClient, vectorStore);
    }
}
