package com.example.claudedemo.agent.rag.embedding;

import java.util.ArrayList;
import java.util.List;

/**
 * Embedding 客户端接口(V2 第八阶段 RAG V3).
 *
 * <p>将文本转换为向量表示;V1 实现为 {@link SimpleHashEmbeddingClient}（本地 FNV 哈希,
 * 不做网络调用）。后续可替换为 OpenAI / 火山等真实 Embedding API。
 *
 * <p><b>契约</b>:
 * <ul>
 *   <li>同文本 → 同向量(确定性)</li>
 *   <li>不同文本 → 不同向量(高概率)</li>
 *   <li>向量已归一化(单位向量,余弦相似度 = 点积)</li>
 *   <li>空文本 / 空白文本 → 返回全零向量</li>
 * </ul>
 *
 * @since 0.0.1
 */
public interface EmbeddingClient {

    /**
     * 将单段文本转为向量.
     */
    EmbeddingVector embed(String text);

    /**
     * 批量将文本转为向量.
     *
     * <p>默认实现逐条调用 {@link #embed(String)};真实 API 可覆写为批量请求。
     */
    default List<EmbeddingVector> embedAll(List<String> texts) {
        if (texts == null) return List.of();
        List<EmbeddingVector> results = new ArrayList<>(texts.size());
        for (String t : texts) {
            results.add(embed(t));
        }
        return results;
    }
}
