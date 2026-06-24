package com.example.claudedemo.agent.memory;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 记忆模块 Spring 装配.
 *
 * <p>将 {@link MemoryProperties} 注册为配置类 bean,并基于其值构造默认
 * {@link CompressionPolicy} bean (基于 turn 数量的策略)。
 *
 * <p>{@link MemoryCompressor} 与 {@link InMemoryConversationStore} 已自带
 * {@code @Component},无需重复注册;此配置仅负责连接二者所需的配置与策略。
 *
 * @since 0.0.1
 */
@Configuration
@EnableConfigurationProperties(MemoryProperties.class)
public class MemoryConfig {

    /**
     * 默认压缩策略:基于 turn 数量,阈值与保留数从 {@link MemoryProperties} 读取.
     */
    @Bean
    public CompressionPolicy turnCountCompressionPolicy(MemoryProperties props) {
        return new TurnCountCompressionPolicy(
                props.getCompressThreshold(),
                props.getKeepRecentTurns());
    }
}
