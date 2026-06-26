package com.example.claudedemo.agent.rag.embedding;

/**
 * Embedding API 调用异常(V2 第九阶段 RAG V4).
 *
 * <p>由 {@link VolcengineEmbeddingClient} 在以下场景统一抛出:
 * <ul>
 *   <li>HTTP 4xx / 5xx(网络错误)</li>
 *   <li>响应为空或不可解析</li>
 *   <li>JSON 格式错误</li>
 *   <li>{@code data} 数组为空或缺失</li>
 *   <li>data 中 {@code index} 缺失或重复</li>
 *   <li>data 中 {@code embedding} 缺失</li>
 *   <li>返回向量维度与配置 {@code dimension} 不一致</li>
 * </ul>
 *
 * @since 0.0.1
 */
public class EmbeddingClientException extends RuntimeException {

    public EmbeddingClientException(String message) {
        super(message);
    }

    public EmbeddingClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
