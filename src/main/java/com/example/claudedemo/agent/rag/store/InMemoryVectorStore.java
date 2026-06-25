package com.example.claudedemo.agent.rag.store;

import com.example.claudedemo.agent.rag.embedding.EmbeddingVector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存向量存储(V2 第八阶段 RAG V3).
 *
 * <p>基于 {@link ConcurrentHashMap} 的暴力余弦相似度搜索。
 * 适用于测试与演示场景,不适用于大规模数据。
 *
 * <p><b>搜索算法</b>:计算 query 与所有存储文档的余弦相似度,
 * 排序后返回 topK。假设向量已归一化(由 {@code EmbeddingClient} 保证),
 * 余弦相似度退化为点积,否则自动执行完整余弦计算。
 *
 * @since 0.0.1
 */
public class InMemoryVectorStore implements VectorStore {

    private static final Logger log = LoggerFactory.getLogger(InMemoryVectorStore.class);

    private final ConcurrentHashMap<String, VectorDocument> store = new ConcurrentHashMap<>();

    @Override
    public void upsert(VectorDocument document) {
        if (document == null || document.id() == null) return;
        store.put(document.id(), document);
    }

    @Override
    public List<VectorSearchResult> search(EmbeddingVector query, int topK) {
        if (query == null || store.isEmpty()) return List.of();
        if (topK <= 0) return List.of();

        List<VectorSearchResult> results = new ArrayList<>();
        for (VectorDocument doc : store.values()) {
            if (doc.embedding() == null) continue;
            double similarity = cosineSimilarity(query, doc.embedding());
            results.add(new VectorSearchResult(doc, similarity));
        }
        results.sort((a, b) -> Double.compare(b.score(), a.score()));
        if (topK < results.size()) {
            results = results.subList(0, topK);
        }
        return List.copyOf(results);
    }

    /**
     * 当前存储的文档数量.
     */
    public int size() {
        return store.size();
    }

    /**
     * 是否为空.
     */
    public boolean isEmpty() {
        return store.isEmpty();
    }

    /**
     * 余弦相似度:规范化计算,不假设向量已归一化.
     */
    private static double cosineSimilarity(EmbeddingVector a, EmbeddingVector b) {
        float[] va = a.values();
        float[] vb = b.values();
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < va.length; i++) {
            dot += va[i] * vb[i];
            normA += va[i] * va[i];
            normB += vb[i] * vb[i];
        }
        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        return (denom < 1e-12) ? 0.0 : dot / denom;
    }

    /**
     * 清空所有记录(测试用).
     */
    public void clear() {
        store.clear();
    }
}
