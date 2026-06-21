package com.example.claudedemo.llm;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * LLM 客户端 V1.
 *
 * <p>基于 Spring 6 RestClient，调用 OpenAI 兼容 Chat Completions 接口（火山 Ark 等）。
 *
 * <p>V1 能力：
 * <ul>
 *   <li>{@link #chat(List)} 发送消息列表，返回首个 choice 的内容与结束原因</li>
 * </ul>
 *
 * @author claude-code
 * @since 0.0.1
 */
@Component
public class LlmClient {

    private final LlmProperties properties;
    private final RestClient restClient;

    public LlmClient(LlmProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.restClient = builder
                .baseUrl(properties.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * 发送对话消息并获取响应.
     *
     * @param messages 消息列表（按 user / assistant / system 顺序）
     * @return LLM 响应（含内容与结束原因）
     * @throws org.springframework.web.client.RestClientException 当 HTTP 调用失败
     */
    public LlmResponse chat(List<ChatMessage> messages) {
        Map<String, Object> body = Map.of(
                "model", properties.getModel(),
                "messages", messages
        );
        OpenAiResponse resp = restClient.post()
                .uri("/chat/completions")
                .body(body)
                .retrieve()
                .body(OpenAiResponse.class);
        Choice first = resp.choices().get(0);
        return new LlmResponse(first.message().content(), first.finishReason());
    }

    // ========== 内部响应结构（OpenAI 协议）==========

    private record OpenAiResponse(List<Choice> choices) {}

    private record Choice(ChatMessage message,
                          @JsonProperty("finish_reason") String finishReason) {}
}
