package com.example.claudedemo.agent.rag;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RagDocument} 单元测试.
 *
 * @since 0.0.1
 */
class RagDocumentTest {

    @Test
    void should_build_with_required_fields() {
        RagDocument doc = new RagDocument("id-1", "title", "content", "source.md");
        assertEquals("id-1", doc.id());
        assertEquals("title", doc.title());
        assertEquals("content", doc.content());
        assertEquals("source.md", doc.source());
        assertEquals(0.0, doc.score());
        assertEquals(List.of(), doc.keywords());
        assertEquals(Map.of(), doc.metadataView());
    }

    @Test
    void should_build_with_keywords() {
        RagDocument doc = new RagDocument("id", "t", "c", "s",
                List.of("users", "city"));
        assertEquals(2, doc.keywords().size());
        assertEquals("users", doc.keywords().get(0));
    }

    @Test
    void should_defensive_copy_keywords() {
        List<String> mutable = new ArrayList<>(List.of("a", "b"));
        RagDocument doc = new RagDocument("id", "t", "c", "s", mutable);
        mutable.add("c");
        assertEquals(2, doc.keywords().size());
    }

    @Test
    void should_treat_null_keywords_as_empty() {
        RagDocument doc = new RagDocument("id", "t", "c", "s", null);
        assertEquals(List.of(), doc.keywords());
        assertThrows(UnsupportedOperationException.class, () -> doc.keywords().add("x"));
    }

    @Test
    void should_treat_null_metadata_as_empty() {
        RagDocument doc = new RagDocument("id", "t", "c", "s", 0.0, List.of(), null);
        assertEquals(Map.of(), doc.metadataView());
    }

    @Test
    void should_clamp_score_to_zero_one() {
        RagDocument neg = new RagDocument("id", "t", "c", "s", -0.5, List.of(), Map.of());
        RagDocument over = new RagDocument("id", "t", "c", "s", 1.5, List.of(), Map.of());
        assertEquals(0.0, neg.score());
        assertEquals(1.0, over.score());
    }

    @Test
    void should_have_record_equality() {
        RagDocument a = new RagDocument("id", "t", "c", "s", 0.5, List.of("k"), Map.of("x", 1));
        RagDocument b = new RagDocument("id", "t", "c", "s", 0.5, List.of("k"), Map.of("x", 1));
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void should_provide_immutable_metadata_view() {
        RagDocument doc = new RagDocument("id", "t", "c", "s", 0.0, List.of(),
                new java.util.HashMap<>(Map.of("k", "v")));
        Map<String, Object> view = doc.metadataView();
        assertEquals("v", view.get("k"));
        assertThrows(UnsupportedOperationException.class, () -> view.put("k2", "v2"));
    }

    @Test
    void should_contain_basic_fields() {
        RagDocument doc = new RagDocument("id", "t", "c", "s", 0.5,
                List.of("k1", "k2"), Map.of("category", "schema"));
        assertNotNull(doc.id());
        assertNotNull(doc.title());
        assertNotNull(doc.content());
        assertNotNull(doc.source());
        assertTrue(doc.score() >= 0.0 && doc.score() <= 1.0);
    }
}
