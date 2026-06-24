package com.example.claudedemo.agent.memory;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 记忆压缩配置.
 *
 * <p>对应 application.yml 中的 {@code memory} 配置块;由 Spring Boot 自动绑定,
 * 注入到 {@link InMemoryConversationStore} 构造压缩策略。
 *
 * <p>默认值与 V1 阶段定稿保持一致:触发阈值 10,保留近期 4 条。
 *
 * @since 0.0.1
 */
@ConfigurationProperties(prefix = "memory")
public class MemoryProperties {

    /** 压缩触发阈值. */
    private int compressThreshold = 10;

    /** 压缩后保留的近期 turn 数. */
    private int keepRecentTurns = 4;

    /** 摘要文本最大字符数(prompt 中约束 LLM 输出). */
    private int summaryMaxChars = 2000;

    public int getCompressThreshold() {
        return compressThreshold;
    }

    public void setCompressThreshold(int compressThreshold) {
        this.compressThreshold = compressThreshold;
    }

    public int getKeepRecentTurns() {
        return keepRecentTurns;
    }

    public void setKeepRecentTurns(int keepRecentTurns) {
        this.keepRecentTurns = keepRecentTurns;
    }

    public int getSummaryMaxChars() {
        return summaryMaxChars;
    }

    public void setSummaryMaxChars(int summaryMaxChars) {
        this.summaryMaxChars = summaryMaxChars;
    }
}
