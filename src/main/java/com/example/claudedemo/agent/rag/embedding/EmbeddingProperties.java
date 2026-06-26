package com.example.claudedemo.agent.rag.embedding;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Embedding 连接配置(V2 第九阶段 RAG V4).
 *
 * <p>对应 application.yml 中的 {@code embedding} 配置块。
 * 与 {@link com.example.claudedemo.agent.rag.RagProperties} 职责分离——
 * RagProperties 管检索行为,本类管 Embedding API 连接信息。
 *
 * <p>默认 provider = SIMPLE_HASH,无需任何外部依赖即可运行。
 *
 * @since 0.0.1
 */
@ConfigurationProperties(prefix = "embedding")
public class EmbeddingProperties {

    /** Embedding 提供者(simple-hash / volcengine). */
    private EmbeddingProvider provider = EmbeddingProvider.SIMPLE_HASH;

    /** 模型名称(如 "doubao-embedding-vision-251215"). */
    private String model = "";

    /** API 地址(如 "https://ark.cn-beijing.volces.com/api/v3/embeddings/multimodal"). */
    private String baseUrl = "";

    /** API Key(建议通过环境变量注入). */
    private String apiKey = "";

    /** 向量维度. */
    private int dimension = 128;

    /** HTTP 超时(秒). */
    private int timeoutSeconds = 10;

    /** 每批文本数,超过此数拆为多个请求. */
    private int batchSize = 16;

    public EmbeddingProvider getProvider() {
        return provider;
    }

    public void setProvider(EmbeddingProvider provider) {
        this.provider = provider;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public int getDimension() {
        return dimension;
    }

    public void setDimension(int dimension) {
        this.dimension = dimension;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }
}
