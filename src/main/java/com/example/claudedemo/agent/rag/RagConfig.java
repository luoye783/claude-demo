package com.example.claudedemo.agent.rag;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * RAG 模块 Spring 装配.
 *
 * <p>将 {@link RagProperties} 注册为配置类 bean;
 * {@link InMemoryRagRetriever} 已自带 {@code @Component},无需重复注册。
 *
 * <p>{@link com.example.claudedemo.agent.mcp.Nl2SqlMcpAgent} 通过
 * {@code ObjectProvider<RagRetriever>} 注入,未装配时降级为"无 RAG"路径,
 * 不影响老测试 trace / messages 形状。
 *
 * @since 0.0.1
 */
@Configuration
@EnableConfigurationProperties(RagProperties.class)
public class RagConfig {
}
