package com.example.claudedemo.agent.rag.store;

import com.example.claudedemo.agent.rag.embedding.EmbeddingVector;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link VectorSearchResult} 单元测试.
 *
 * @since 0.0.1
 */
class VectorSearchResultTest {

    private static final VectorDocument DOC = new VectorDocument("id", "c", "s",
            Map.of(), new EmbeddingVector(new float[]{1f}, 1));

    @Test
    void should_build_with_document_and_score() {
        VectorSearchResult r = new VectorSearchResult(DOC, 0.85);
        assertEquals(DOC, r.document());
        assertEquals(0.85, r.score());
    }

    @Test
    void should_have_record_equality() {
        VectorSearchResult a = new VectorSearchResult(DOC, 0.9);
        VectorSearchResult b = new VectorSearchResult(DOC, 0.9);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void should_allow_negative_score() {
        VectorSearchResult r = new VectorSearchResult(DOC, -0.5);
        assertEquals(-0.5, r.score());
    }
}
