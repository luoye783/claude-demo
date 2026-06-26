package com.example.claudedemo.agent.rag.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 火山引擎 Ark 多模态 Embedding 客户端(V2 第九阶段 RAG V4).
 *
 * <p>基于 Spring {@link RestClient},调用火山方舟的多模态 Embedding API。
 * 请求体使用 OpenAI 兼容格式,支持文本和图片输入;V1 仅使用文本输入。
 *
 * <p><b>批处理</b>:{@link #embedAll(List)} 按 {@code batchSize} 拆批调用 API,
 * 每批独立请求,最后按 index 全局排序以保持返回顺序与输入顺序一致。
 *
 * <p><b>错误处理</b>:所有真实 API 相关错误统一包装为 {@link EmbeddingClientException},
 * 包括 HTTP 报错、响应解析失败、维度校验失败等。
 *
 * @since 0.0.1
 */
public class VolcengineEmbeddingClient implements EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(VolcengineEmbeddingClient.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RestClient restClient;
    private final EmbeddingProperties props;

    /**
     * @param props Embedding 连接配置(不可为 null)
     */
    public VolcengineEmbeddingClient(EmbeddingProperties props) {
        this(validateProps(props), buildRestClient(props));
    }

    /**
     * 包级测试构造器:接受预构造的 RestClient(用于 mock 测试).
     */
    VolcengineEmbeddingClient(EmbeddingProperties props, RestClient restClient) {
        if (props == null) {
            throw new IllegalArgumentException("EmbeddingProperties must not be null");
        }
        if (props.getBaseUrl() == null || props.getBaseUrl().isBlank()) {
            throw new IllegalArgumentException("embedding.base-url must not be blank");
        }
        if (props.getApiKey() == null || props.getApiKey().isBlank()) {
            throw new IllegalArgumentException("embedding.api-key must not be blank; " +
                    "set EMBEDDING_API_KEY environment variable");
        }
        if (props.getModel() == null || props.getModel().isBlank()) {
            throw new IllegalArgumentException("embedding.model must not be blank");
        }
        this.props = props;
        this.restClient = restClient;
    }

    private static EmbeddingProperties validateProps(EmbeddingProperties props) {
        if (props == null) throw new IllegalArgumentException("EmbeddingProperties must not be null");
        return props;
    }

    private static RestClient buildRestClient(EmbeddingProperties props) {
        return RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .build();
    }

    @Override
    public EmbeddingVector embed(String text) {
        List<EmbeddingVector> results = embedAll(List.of(text));
        if (results.isEmpty()) {
            throw new EmbeddingClientException("embed returned empty result for text: " + text);
        }
        return results.get(0);
    }

    /**
     * 按 batchSize 拆批调用 API,合并结果并保持顺序。
     *
     * <p>每批请求独立发送 HTTP 请求;失败时整批抛 {@link EmbeddingClientException}。
     * 所有批次结果按 index 全局排序后返回。
     */
    @Override
    public List<EmbeddingVector> embedAll(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        int batchSize = props.getBatchSize();
        if (batchSize <= 0) batchSize = texts.size();

        List<EmbeddingVectorEntry> allEntries = new ArrayList<>();
        for (int start = 0; start < texts.size(); start += batchSize) {
            int end = Math.min(start + batchSize, texts.size());
            List<String> batch = texts.subList(start, end);
            List<EmbeddingVectorEntry> batchResults = callApi(batch, start);
            allEntries.addAll(batchResults);
        }
        // 按 index 全局排序,保证返回顺序与输入顺序一致
        allEntries.sort(Comparator.comparingInt(EmbeddingVectorEntry::index));
        return allEntries.stream()
                .map(EmbeddingVectorEntry::vector)
                .toList();
    }

    /**
     * 调用一次 Embedding API,解析响应.
     */
    private List<EmbeddingVectorEntry> callApi(List<String> texts, int baseIndex) {
        try {
            String requestJson = buildRequest(texts);
            String responseBody = restClient.post()
                    .body(requestJson)
                    .retrieve()
                    .body(String.class);

            if (responseBody == null || responseBody.isBlank()) {
                throw new EmbeddingClientException("Embedding API 返回空响应");
            }
            return parseResponse(responseBody, baseIndex);
        } catch (EmbeddingClientException e) {
            throw e;
        } catch (Exception e) {
            throw new EmbeddingClientException("Embedding API 调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 构造请求体 JSON.
     */
    private String buildRequest(List<String> texts) {
        try {
            StringBuilder sb = new StringBuilder(256);
            sb.append("{\"model\":\"").append(escape(props.getModel()))
              .append("\",\"input\":[");
            for (int i = 0; i < texts.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("{\"type\":\"text\",\"text\":\"")
                  .append(escape(texts.get(i)))
                  .append("\"}");
            }
            sb.append("]}");
            return sb.toString();
        } catch (Exception e) {
            throw new EmbeddingClientException("构造请求体失败: " + e.getMessage(), e);
        }
    }

    /**
     * 解析响应 JSON,提取 embedding 数组.
     */
    private List<EmbeddingVectorEntry> parseResponse(String responseBody, int baseIndex) {
        JsonNode root;
        try {
            root = MAPPER.readTree(responseBody);
        } catch (Exception e) {
            throw new EmbeddingClientException("API 响应 JSON 解析失败: " + e.getMessage());
        }

        // data 存在性校验
        JsonNode data = root.path("data");
        if (data.isMissingNode() || !data.isArray()) {
            throw new EmbeddingClientException("API 响应缺少 data 数组");
        }
        if (data.isEmpty()) {
            throw new EmbeddingClientException("API 响应 data 数组为空");
        }

        List<EmbeddingVectorEntry> entries = new ArrayList<>();
        int expectedDim = props.getDimension();

        for (JsonNode item : data) {
            // index
            JsonNode idxNode = item.path("index");
            if (idxNode.isMissingNode() || !idxNode.isInt()) {
                throw new EmbeddingClientException("API 响应 data 元素缺少有效 index");
            }
            int globalIndex = baseIndex + idxNode.asInt();

            // embedding
            JsonNode embNode = item.path("embedding");
            if (embNode.isMissingNode() || !embNode.isArray()) {
                throw new EmbeddingClientException("API 响应 data 元素缺少 embedding 数组");
            }

            List<Float> values = new ArrayList<>();
            for (JsonNode v : embNode) {
                values.add((float) v.asDouble());
            }
            if (values.isEmpty()) {
                throw new EmbeddingClientException("API 响应 embedding 数组为空");
            }

            // 维度校验
            if (values.size() != expectedDim) {
                throw new EmbeddingClientException(
                        "返回向量维度 " + values.size() + " 与配置 dimension=" + expectedDim + " 不一致");
            }

            float[] arr = new float[values.size()];
            for (int i = 0; i < values.size(); i++) {
                arr[i] = values.get(i);
            }
            entries.add(new EmbeddingVectorEntry(globalIndex, new EmbeddingVector(arr, expectedDim)));
        }

        return entries;
    }

    /**
     * JSON 转义: " → \", \ → \\, \n → \\n, \t → \\t
     */
    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }

    /**
     * 带全局 index 的内部条目,用于跨批次排序.
     */
    private record EmbeddingVectorEntry(int index, EmbeddingVector vector) {
    }
}
