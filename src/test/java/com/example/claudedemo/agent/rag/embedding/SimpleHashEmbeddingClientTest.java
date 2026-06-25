package com.example.claudedemo.agent.rag.embedding;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SimpleHashEmbeddingClient} 单元测试.
 *
 * @since 0.0.1
 */
class SimpleHashEmbeddingClientTest {

    private final SimpleHashEmbeddingClient client = new SimpleHashEmbeddingClient(128);

    @Test
    void same_input_produces_same_vector() {
        EmbeddingVector a = client.embed("users 表怎么用");
        EmbeddingVector b = client.embed("users 表怎么用");
        assertEquals(a, b);
    }

    @Test
    void different_input_produces_different_vector() {
        EmbeddingVector a = client.embed("users 表怎么用");
        EmbeddingVector b = client.embed("如何查询城市");
        assertNotEquals(a, b);
    }

    @Test
    void dimension_matches_config() {
        SimpleHashEmbeddingClient c64 = new SimpleHashEmbeddingClient(64);
        assertEquals(64, c64.embed("test").dimension());
        assertEquals(128, client.embed("test").dimension());
    }

    @Test
    void vector_is_normalized_unit_vector() {
        EmbeddingVector v = client.embed("hello world");
        double norm = 0;
        for (float f : v.values()) norm += f * f;
        norm = Math.sqrt(norm);
        assertEquals(1.0, norm, 1e-6, "归一化后的向量 L2 norm 应 ≈ 1.0");
    }

    @Test
    void empty_text_returns_zero_vector() {
        EmbeddingVector empty = client.embed("");
        EmbeddingVector blank = client.embed("   ");
        EmbeddingVector nul = client.embed(null);

        for (float f : empty.values()) assertEquals(0.0f, f);
        for (float f : blank.values()) assertEquals(0.0f, f);
        for (float f : nul.values()) assertEquals(0.0f, f);
    }

    @Test
    void embed_all_returns_correct_count() {
        List<String> texts = List.of("用户", "城市", "状态");
        List<EmbeddingVector> vecs = client.embedAll(texts);
        assertEquals(3, vecs.size());
        // 与单条 embed 一致
        assertEquals(client.embed("用户"), vecs.get(0));
        assertEquals(client.embed("城市"), vecs.get(1));
    }

    @Test
    void embed_all_empty_input_returns_empty() {
        assertTrue(client.embedAll(List.of()).isEmpty());
        assertTrue(client.embedAll(null).isEmpty());
    }

    @Test
    void fnv1a_seed_deterministic() {
        long s1 = SimpleHashEmbeddingClient.fnv1aSeed("users");
        long s2 = SimpleHashEmbeddingClient.fnv1aSeed("users");
        assertEquals(s1, s2);
    }

    @Test
    void fnv1a_seed_differs_for_different_inputs() {
        long s1 = SimpleHashEmbeddingClient.fnv1aSeed("users");
        long s2 = SimpleHashEmbeddingClient.fnv1aSeed("user");
        assertNotEquals(s1, s2);
    }

    @Test
    void normalize_does_not_mutate_zero_vector() {
        float[] zero = new float[128];
        SimpleHashEmbeddingClient.normalize(zero);
        for (float f : zero) assertEquals(0.0f, f);
    }

    @Test
    void every_vector_has_valid_dimension() {
        EmbeddingVector v = client.embed("any text");
        assertEquals(128, v.dimension());
        assertEquals(128, v.values().length);
    }
}
