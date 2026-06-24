package com.example.claudedemo.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * LLM 客户端.
 *
 * <p>基于 Spring 6 RestClient,调用 OpenAI 兼容 Chat Completions 接口（火山 Ark 等）。
 *
 * <p>V1 能力：
 * <ul>
 *   <li>{@link #chat(List)} 发送消息列表,返回首个 choice 的内容与结束原因</li>
 * </ul>
 *
 * <p><b>Tool Calling 扩展</b>(V1 新增)：
 * <ul>
 *   <li>{@link #chatWithTools(List, List)} 携带 {@code tools} 字段,响应可含 {@code tool_calls}</li>
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
     * <p>V1 普通文本对话入口,响应中 {@code tool_calls} 字段(V1 协议下不会出现)被忽略,
     * 永远返回 {@link LlmResponse} 的 {@code (content, finishReason)} 形式。
     *
     * @param messages 消息列表(按 user / assistant / system 顺序)
     * @return LLM 响应(含内容与结束原因)
     * @throws org.springframework.web.client.RestClientException 当 HTTP 调用失败
     */
    public LlmResponse chat(List<ChatMessage> messages) {
        OpenAiResponse resp = restClient.post()
                .uri("/chat/completions")
                .body(buildBody(messages, null))
                .retrieve()
                .body(OpenAiResponse.class);
        Choice first = resp.choices().get(0);
        return new LlmResponse(first.message().content(), first.finishReason());
    }

    /**
     * 发送对话消息并携带工具定义,响应可包含 {@code tool_calls}.
     *
     * <p>与 {@link #chat(List)} 的差异:
     * <ul>
     *   <li>请求体额外包含 {@code tools} 字段(OpenAI function calling 协议)</li>
     *   <li>响应 {@link LlmResponse#toolCalls()} 可能非空,需要进入工具执行循环</li>
     * </ul>
     *
     * <p>{@code tools} 为空或 {@code null} 时行为退化为 {@link #chat(List)}(不发送 {@code tools} 字段)。
     *
     * @param messages 消息列表(OpenAI 协议,按 user / assistant / system / tool 顺序)
     * @param tools    LLM 可调用的工具定义(V1: get_schema、execute_sql)
     * @return LLM 响应(content + finishReason + 可选 toolCalls)
     * @throws org.springframework.web.client.RestClientException 当 HTTP 调用失败
     */
    public LlmResponse chatWithTools(List<ChatMessage> messages, List<ToolDefinition> tools) {
        OpenAiResponse resp = restClient.post()
                .uri("/chat/completions")
                .body(buildBody(messages, tools))
                .retrieve()
                .body(OpenAiResponse.class);
        if (resp == null || resp.choices()==null){
            throw new RuntimeException("chatWithTools cant get any response,please check llm api");
        }
        if (resp.choices().isEmpty()){
            throw new RuntimeException("chatWithTools  response not contain choice,please check llm api");
        }
        Choice first = resp.choices().getFirst();
        return new LlmResponse(
                first.message().content(),
                first.finishReason(),
                first.message().toolCalls()
        );
    }

    /**
     * 构造 OpenAI 请求体.
     *
     * <p>使用 {@link HashMap} 而非 {@link Map#of} 以避免 {@code null} 值限制;
     * 当 {@code tools} 为空或 {@code null} 时不发送 {@code tools} 字段,与 V1 行为一致。
     */
    private Map<String, Object> buildBody(List<ChatMessage> messages, List<ToolDefinition> tools) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", properties.getModel());
        body.put("messages", messages);
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", wrapTools(tools));
        }
        return body;
    }

    /**
     * 将语义层 {@link ToolDefinition} 包装为 OpenAI function calling 线缆结构.
     */
    private List<Map<String, Object>> wrapTools(List<ToolDefinition> tools) {
        List<Map<String, Object>> wrapped = new ArrayList<>(tools.size());
        for (ToolDefinition t : tools) {
            Map<String, Object> function = new HashMap<>();
            function.put("name", t.name());
            function.put("description", t.description());
            function.put("parameters", t.parameters());

            Map<String, Object> tool = new HashMap<>();
            tool.put("type", "function");
            tool.put("function", function);
            wrapped.add(tool);
        }
        return wrapped;
    }

    // ========== 内部响应结构（OpenAI 协议）==========
    // ChatMessage 已包含 tool_calls 字段(@JsonProperty + @JsonInclude(NON_NULL)),
    // 普通响应中该字段为 null,V1 chat() 自动忽略;chatWithTools() 主动读取。

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OpenAiResponse(List<Choice> choices) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Choice(ChatMessage message,
                          @JsonProperty("finish_reason") String finishReason) {
    }
}
