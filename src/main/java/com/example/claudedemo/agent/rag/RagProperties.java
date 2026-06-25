package com.example.claudedemo.agent.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RAG V1 配置.
 *
 * <p>对应 application.yml 中的 {@code rag} 配置块;由 Spring Boot 自动绑定,
 * 注入到 {@link com.example.claudedemo.agent.mcp.Nl2SqlMcpAgent} 控制检索行为。
 *
 * <p><b>默认值</b>:与设计文档保持一致 —— topK=3, min-score=0.05, max-content-chars=1500。
 *
 * @since 0.0.1
 */
@ConfigurationProperties(prefix = "rag")
public class RagProperties {

    /** 总开关(V2 切真实检索器时可临时禁用). */
    private boolean enabled = true;

    /** 检索返回文档数上限. */
    private int topK = 3;

    /** 命中阈值,score < min-score 视为不命中. */
    private double minScore = 0.05;

    /** 注入 system 消息的 RAG 总字符数上限,防止挤占 turns 空间. */
    private int maxContentChars = 1500;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public double getMinScore() {
        return minScore;
    }

    public void setMinScore(double minScore) {
        this.minScore = minScore;
    }

    public int getMaxContentChars() {
        return maxContentChars;
    }

    public void setMaxContentChars(int maxContentChars) {
        this.maxContentChars = maxContentChars;
    }
}
