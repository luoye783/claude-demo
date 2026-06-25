package com.example.claudedemo.agent.rag.store;

import com.example.claudedemo.agent.rag.embedding.EmbeddingVector;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link VectorDocument} 单元测试.
 *
 * @since 0.0.1
 */
class VectorDocumentTest {

    private static final EmbeddingVector EMB = new EmbeddingVector(new float[]{1f, 0f}, 2);

    @Test
    void should_build_with_required_fields() {
        VectorDocument d = new VectorDocument("id", "content", "s.md", Map.of(), EMB);
        assertEquals("id", d.id());
        assertEquals("content", d.content());
        assertEquals("s.md", d.source());
        assertEquals(EMB, d.embedding());
    }

    @Test
    void should_treat_null_metadata_as_empty() {
        VectorDocument d = new VectorDocument("id", "c", "s", null, EMB);
        assertEquals(Map.of(), d.metadataView());
    }

    @Test
    void should_provide_immutable_metadata_view() {
        VectorDocument d = new VectorDocument("id", "c", "s",
                new java.util.HashMap<>(Map.of("k", "v")), EMB);
        assertEquals("v", d.metadataView().get("k"));
        assertThrows(UnsupportedOperationException.class, () -> d.metadataView().put("k2", "v2"));
    }

    @Test
    void should_have_record_equality() {
        VectorDocument a = new VectorDocument("id", "c", "s", Map.of(), EMB);
        VectorDocument b = new VectorDocument("id", "c", "s", Map.of(), EMB);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotNull(a.id());
    }
}
