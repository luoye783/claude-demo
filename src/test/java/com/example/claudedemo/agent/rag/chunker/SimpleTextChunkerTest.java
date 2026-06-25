package com.example.claudedemo.agent.rag.chunker;

import com.example.claudedemo.agent.rag.KnowledgeChunk;
import com.example.claudedemo.agent.rag.KnowledgeDocument;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SimpleTextChunker} 单元测试.
 *
 * @since 0.0.1
 */
class SimpleTextChunkerTest {

    private static KnowledgeDocument doc(String id, String title, String content) {
        return new KnowledgeDocument(id, title, content, "src/" + id + ".md", Map.of());
    }

    @Test
    void should_return_one_chunk_when_content_short() {
        String text = "hello world";
        SimpleTextChunker chunker = new SimpleTextChunker(800, 100);
        List<KnowledgeChunk> chunks = chunker.chunk(doc("test", "Test", text));
        assertEquals(1, chunks.size());
        assertEquals(text, chunks.get(0).content());
        assertEquals(0, chunks.get(0).chunkIndex());
    }

    @Test
    void should_return_empty_when_content_empty() {
        SimpleTextChunker chunker = new SimpleTextChunker(800, 100);
        assertTrue(chunker.chunk(doc("e", "E", "")).isEmpty());
        assertTrue(chunker.chunk(doc("e", "E", null)).isEmpty());
        assertTrue(chunker.chunk(doc("e", "E", "   ")).isEmpty());
    }

    @Test
    void should_return_empty_when_document_null() {
        SimpleTextChunker chunker = new SimpleTextChunker(800, 100);
        assertTrue(chunker.chunk(null).isEmpty());
    }

    @Test
    void should_split_into_multiple_chunks() {
        SimpleTextChunker chunker = new SimpleTextChunker(10, 2);
        List<KnowledgeChunk> chunks = chunker.chunk(doc("test", "Test", "0123456789abcdefghij"));
        assertEquals(3, chunks.size());
        assertEquals("0123456789", chunks.get(0).content());
        assertEquals("89abcdefgh", chunks.get(1).content());
        assertEquals("ghij", chunks.get(2).content());
    }

    @Test
    void should_set_correct_chunk_index() {
        SimpleTextChunker chunker = new SimpleTextChunker(5, 1);
        List<KnowledgeChunk> chunks = chunker.chunk(doc("test", "Test", "abcdefghijk"));
        for (int i = 0; i < chunks.size(); i++) {
            assertEquals(i, chunks.get(i).chunkIndex(), "chunk " + i + " index wrong");
        }
    }

    @Test
    void should_generate_correct_chunk_ids() {
        SimpleTextChunker chunker = new SimpleTextChunker(5, 1);
        List<KnowledgeChunk> chunks = chunker.chunk(doc("doc-users", "U", "abcdefghijk"));
        for (int i = 0; i < chunks.size(); i++) {
            assertEquals("doc-users-chunk-" + i, chunks.get(i).id());
        }
    }

    @Test
    void should_carry_document_id_and_title_into_chunks() {
        SimpleTextChunker chunker = new SimpleTextChunker(3, 0);
        List<KnowledgeChunk> chunks = chunker.chunk(doc("abc", "TestDoc", "123456"));
        for (KnowledgeChunk c : chunks) {
            assertEquals("abc", c.documentId());
            assertEquals("TestDoc", c.title());
        }
    }

    @Test
    void should_preserve_source() {
        SimpleTextChunker chunker = new SimpleTextChunker(3, 0);
        KnowledgeDocument d = new KnowledgeDocument("x", "X", "abcdef", "my-source.md", Map.of());
        List<KnowledgeChunk> chunks = chunker.chunk(d);
        chunks.forEach(c -> assertEquals("my-source.md", c.source()));
    }

    @Test
    void should_adjust_overlap_greater_than_chunkSize() {
        SimpleTextChunker chunker = new SimpleTextChunker(10, 20);
        KnowledgeDocument d = new KnowledgeDocument("x", "X", "0123456789abcdefghij", "s", Map.of());
        List<KnowledgeChunk> chunks = chunker.chunk(d);
        assertFalse(chunks.isEmpty());
        assertEquals(3, chunks.size());
        assertEquals("56789abcde", chunks.get(1).content());
    }

    @Test
    void should_reject_negative_chunkSize() {
        assertThrows(IllegalArgumentException.class, () -> new SimpleTextChunker(0, 0));
        assertThrows(IllegalArgumentException.class, () -> new SimpleTextChunker(-1, 0));
    }

    @Test
    void should_reject_negative_overlap() {
        assertThrows(IllegalArgumentException.class, () -> new SimpleTextChunker(10, -1));
    }

    @Test
    void should_handle_content_exactly_at_chunkSize() {
        String text = "1234567890";
        SimpleTextChunker chunker = new SimpleTextChunker(10, 2);
        List<KnowledgeChunk> chunks = chunker.chunk(doc("t", "T", text));
        assertEquals(1, chunks.size());
        assertEquals(text, chunks.get(0).content());
    }

    @Test
    void should_handle_content_one_character_over_chunkSize() {
        SimpleTextChunker chunker = new SimpleTextChunker(10, 2);
        List<KnowledgeChunk> chunks = chunker.chunk(doc("t", "T", "0123456789x"));
        assertEquals(2, chunks.size());
        assertEquals("0123456789", chunks.get(0).content());
        assertEquals("89x", chunks.get(1).content());
    }

    @Test
    void should_set_metadata_with_chunkSize_and_overlap() {
        SimpleTextChunker chunker = new SimpleTextChunker(100, 10);
        List<KnowledgeChunk> chunks = chunker.chunk(doc("t", "T", "a".repeat(200)));
        KnowledgeChunk first = chunks.get(0);
        assertNotNull(first.metadataView());
        assertEquals(100, first.metadataView().get("chunkSize"));
        assertEquals(10, first.metadataView().get("overlap"));
    }
}
