package com.example.claudedemo.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LLM 配置.
 *
 * <p>对应 application.yml 中的 {@code llm.*} 配置项。
 *
 * @author claude-code
 * @since 0.0.1
 */
@ConfigurationProperties(prefix = "llm")
public class LlmProperties {

    /** 基础 URL，如 https://ark.cn-beijing.volces.com/api/v3. */
    private String baseUrl;

    /** API Key（推荐通过环境变量注入）.*/
    private String apiKey;

    /** 模型名，如 minimax-m3 / ep-xxxx. */
    private String model;

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

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }
}
