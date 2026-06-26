package com.example.claudedemo.agent.rag.hybrid;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Hybrid 检索配置(V2 第十二阶段 RAG V7).
 *
 * <p>对应 application.yml 中的 {@code rag.hybrid} 配置块。
 *
 * @since 0.0.1
 */
@ConfigurationProperties(prefix = "rag.hybrid")
public class HybridProperties {

    /** 关键词检索候选集大小. */
    private int keywordTopK = 20;

    /** 向量检索候选集大小. */
    private int vectorTopK = 20;

    /** 融合后最终返回数. */
    private int finalTopK = 3;

    /** RRF 融合常数 K,越大 rank 影响越小. */
    private int rrfK = 60;

    public int getKeywordTopK() {
        return keywordTopK;
    }

    public void setKeywordTopK(int keywordTopK) {
        this.keywordTopK = keywordTopK;
    }

    public int getVectorTopK() {
        return vectorTopK;
    }

    public void setVectorTopK(int vectorTopK) {
        this.vectorTopK = vectorTopK;
    }

    public int getFinalTopK() {
        return finalTopK;
    }

    public void setFinalTopK(int finalTopK) {
        this.finalTopK = finalTopK;
    }

    public int getRrfK() {
        return rrfK;
    }

    public void setRrfK(int rrfK) {
        this.rrfK = rrfK;
    }
}
