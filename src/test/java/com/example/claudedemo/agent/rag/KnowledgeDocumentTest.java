package com.example.claudedemo.agent.rag;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link KnowledgeDocument} 单元测试.
 *
 * @since 0.0.1
 */
class KnowledgeDocumentTest {

    @Test
    void should_build_with_required_fields() {
        KnowledgeDocument doc = new KnowledgeDocument("users", "users 表",
                "content", "knowledge-base/users.md", Map.of());
        assertEquals("users", doc.id());
        assertEquals("users 表", doc.title());
        assertEquals("content", doc.content());
        assertEquals("knowledge-base/users.md", doc.source());
        assertEquals(Map.of(), doc.metadataView());
    }

    @Test
    void should_treat_null_metadata_as_empty() {
        KnowledgeDocument doc = new KnowledgeDocument("u", "t", "c", "s", null);
        assertEquals(Map.of(), doc.metadataView());
    }

    @Test
    void should_provide_immutable_metadata_view() {
        KnowledgeDocument doc = new KnowledgeDocument("u", "t", "c", "s",
                new HashMap<>(Map.of("k", "v")));
        assertEquals("v", doc.metadataView().get("k"));
        assertThrows(UnsupportedOperationException.class, () -> doc.metadataView().put("k2", "v2"));
    }

    @Test
    void should_have_record_equality() {
        KnowledgeDocument a = new KnowledgeDocument("u", "t", "c", "s", Map.of("x", 1));
        KnowledgeDocument b = new KnowledgeDocument("u", "t", "c", "s", Map.of("x", 1));
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotNull(a.id());
    }
}
