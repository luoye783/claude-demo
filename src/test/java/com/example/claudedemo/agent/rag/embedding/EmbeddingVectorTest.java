package com.example.claudedemo.agent.rag.embedding;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link EmbeddingVector} 单元测试.
 *
 * @since 0.0.1
 */
class EmbeddingVectorTest {

    @Test
    void should_build_with_valid_dimension() {
        float[] vals = {1.0f, 0.0f, -1.0f};
        EmbeddingVector v = new EmbeddingVector(vals, 3);
        assertEquals(3, v.dimension());
        assertArrayEquals(new float[]{1.0f, 0.0f, -1.0f}, v.values());
    }

    @Test
    void should_reject_null_values() {
        assertThrows(IllegalArgumentException.class, () -> new EmbeddingVector(null, 3));
    }

    @Test
    void should_reject_zero_dimension() {
        assertThrows(IllegalArgumentException.class, () -> new EmbeddingVector(new float[0], 0));
        assertThrows(IllegalArgumentException.class, () -> new EmbeddingVector(new float[1], -1));
    }

    @Test
    void should_reject_mismatched_dimension() {
        float[] vals = {1.0f, 2.0f, 3.0f};
        assertThrows(IllegalArgumentException.class, () -> new EmbeddingVector(vals, 2));
        assertThrows(IllegalArgumentException.class, () -> new EmbeddingVector(vals, 5));
    }

    @Test
    void should_defensive_copy_values() {
        float[] orig = {1.0f, 2.0f};
        EmbeddingVector v = new EmbeddingVector(orig, 2);
        orig[0] = 999f;
        assertEquals(1.0f, v.values()[0]);
    }

    @Test
    void should_equality_by_content() {
        EmbeddingVector a = new EmbeddingVector(new float[]{1f, 2f, 3f}, 3);
        EmbeddingVector b = new EmbeddingVector(new float[]{1f, 2f, 3f}, 3);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void should_not_be_null() {
        EmbeddingVector v = new EmbeddingVector(new float[]{0.5f, -0.5f}, 2);
        assertNotNull(v);
        assertNotNull(v.values());
    }
}
