package com.example.claudedemo.agent.rag.embedding;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Embedding 模块 Spring 装配(V2 第九阶段 RAG V4).
 *
 * <p>根据 {@link EmbeddingProperties#getProvider()} 选择对应的 {@link EmbeddingClient} 实现:
 * <ul>
 *   <li>{@code SIMPLE_HASH} → {@link SimpleHashEmbeddingClient}(默认,测试/本地)</li>
 *   <li>{@code VOLCENGINE} → {@link VolcengineEmbeddingClient}(真实 API)</li>
 * </ul>
 *
 * <p>{@code RagConfig} 不再新建 EmbeddingClient 实现,改为注入本配置类提供的 bean。
 *
 * @since 0.0.1
 */
@Configuration
@EnableConfigurationProperties(EmbeddingProperties.class)
public class EmbeddingConfig {

    @Bean
    public EmbeddingClient embeddingClient(EmbeddingProperties props) {
        return switch (props.getProvider()) {
            case VOLCENGINE -> new VolcengineEmbeddingClient(props);
            case SIMPLE_HASH -> new SimpleHashEmbeddingClient(props);
        };
    }
}
