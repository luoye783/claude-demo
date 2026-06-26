package com.example.claudedemo.agent.rag.embedding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 确定性哈希 Embedding 客户端(V2 第八阶段 RAG V3).
 *
 * <p>使用 FNV-1a hash 作为 {@link java.util.Random} 种子,生成固定维度随机向量。
 * <b>仅用于测试与演示</b>,不代表真实 embedding 质量。
 *
 * <p><b>特性</b>:
 * <ul>
 *   <li>同文本 → 同向量(确定性,基于 FNV-1a seed)</li>
 *   <li>向量已归一化(L2 norm ≈ 1.0)</li>
 *   <li>空文本 → 全零向量(score 始终为 0,不会被召回)</li>
 * </ul>
 *
 * @since 0.0.1
 */
public class SimpleHashEmbeddingClient implements EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(SimpleHashEmbeddingClient.class);

    /** FNV-1a offset basis (64-bit). */
    private static final long FNV_OFFSET = 1469598103934665603L;
    /** FNV-1a prime (64-bit). */
    private static final long FNV_PRIME = 1099511628211L;

    private final int dimension;

    /**
     * @param dimension 向量维度,来自 {@link RagProperties#getEmbeddingDimension()}
     */
    public SimpleHashEmbeddingClient(int dimension) {
        if (dimension <= 0) {
            throw new IllegalArgumentException("dimension must be > 0, actual: " + dimension);
        }
        this.dimension = dimension;
    }

    /**
     * 便捷构造器,从 {@link EmbeddingProperties} 取值.
     */
    public SimpleHashEmbeddingClient(EmbeddingProperties props) {
        this(props == null ? 128 : props.getDimension());
    }

    @Override
    public EmbeddingVector embed(String text) {
        if (text == null || text.isBlank()) {
            return zeroVector();
        }
        Random rng = new Random(fnv1aSeed(text));
        float[] vec = new float[dimension];
        for (int i = 0; i < dimension; i++) {
            vec[i] = (rng.nextFloat() * 2.0f) - 1.0f;
        }
        normalize(vec);
        return new EmbeddingVector(vec, dimension);
    }

    @Override
    public List<EmbeddingVector> embedAll(List<String> texts) {
        if (texts == null) return List.of();
        List<EmbeddingVector> result = new ArrayList<>(texts.size());
        for (String t : texts) {
            result.add(embed(t));
        }
        return result;
    }

    private EmbeddingVector zeroVector() {
        return new EmbeddingVector(new float[dimension], dimension);
    }

    /**
     * FNV-1a 64-bit hash → long seed.
     */
    static long fnv1aSeed(String text) {
        long hash = FNV_OFFSET;
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        for (byte b : bytes) {
            hash ^= (b & 0xff);
            hash *= FNV_PRIME;
        }
        return hash;
    }

    /**
     * L2 归一化:各分量 /= norm。norm = 0 时不操作(全零向量保持原样).
     */
    static void normalize(float[] v) {
        double norm = 0;
        for (float f : v) norm += f * f;
        norm = Math.sqrt(norm);
        if (norm > 1e-12) {
            for (int i = 0; i < v.length; i++) {
                v[i] /= (float) norm;
            }
        }
    }
}
