package com.example.claudedemo.agent.rag;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link KnowledgeChunk} 单元测试.
 *
 * @since 0.0.1
 */
class KnowledgeChunkTest {

    @Test
    void should_build_with_required_fields() {
        KnowledgeChunk c = new KnowledgeChunk("users-chunk-0", "users",
                "users 表", "content", "knowledge-base/users.md", 0, Map.of());
        assertEquals("users-chunk-0", c.id());
        assertEquals("users", c.documentId());
        assertEquals("users 表", c.title());
        assertEquals("content", c.content());
        assertEquals("knowledge-base/users.md", c.source());
        assertEquals(0, c.chunkIndex());
        assertEquals(Map.of(), c.metadataView());
    }

    @Test
    void should_treat_null_metadata_as_empty() {
        KnowledgeChunk c = new KnowledgeChunk("id", "did", "t", "c", "s", 0, null);
        assertEquals(Map.of(), c.metadataView());
    }

    @Test
    void should_provide_immutable_metadata_view() {
        KnowledgeChunk c = new KnowledgeChunk("id", "did", "t", "c", "s", 0,
                new HashMap<>(Map.of("k", "v")));
        assertEquals("v", c.metadataView().get("k"));
        assertThrows(UnsupportedOperationException.class, () -> c.metadataView().put("k2", "v2"));
    }

    @Test
    void should_have_record_equality() {
        KnowledgeChunk a = new KnowledgeChunk("id", "did", "t", "c", "s", 1, Map.of());
        KnowledgeChunk b = new KnowledgeChunk("id", "did", "t", "c", "s", 1, Map.of());
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotNull(a.id());
    }

    @Test
    void should_preserve_chunkIndex() {
        KnowledgeChunk c = new KnowledgeChunk("x-chunk-5", "x", "t", "c", "s", 5, Map.of());
        assertEquals(5, c.chunkIndex());
    }

    @Test
    void should_preserve_documentId() {
        KnowledgeChunk c = new KnowledgeChunk("c", "doc-users", "t", "c", "s", 0, Map.of());
        assertEquals("doc-users", c.documentId());
    }
}
