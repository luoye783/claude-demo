package com.example.claudedemo.agent.rag.index;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * {@link HashUtils} 单元测试.
 *
 * @since 0.0.1
 */
class HashUtilsTest {

    @Test
    void sha256_should_be_deterministic() {
        String h1 = HashUtils.sha256("hello");
        String h2 = HashUtils.sha256("hello");
        assertEquals(h1, h2);
        assertEquals(64, h1.length());
    }

    @Test
    void sha256_different_input_different_hash() {
        assertNotEquals(HashUtils.sha256("hello"), HashUtils.sha256("world"));
    }

    @Test
    void sha256_empty_input() {
        String hash = HashUtils.sha256("");
        assertEquals(64, hash.length());
    }

    @Test
    void documentHash_should_be_stable() {
        String content = "users 表存储用户基础信息。";
        assertEquals(HashUtils.documentHash(content), HashUtils.documentHash(content));
    }

    @Test
    void documentHash_different_content_different_hash() {
        assertNotEquals(
                HashUtils.documentHash("aaa"),
                HashUtils.documentHash("bbb"));
    }

    @Test
    void chunkHash_should_be_stable() {
        String h1 = HashUtils.chunkHash("content", "src/doc.md", 0);
        String h2 = HashUtils.chunkHash("content", "src/doc.md", 0);
        assertEquals(h1, h2);
    }

    @Test
    void chunkHash_different_content_different_hash() {
        assertNotEquals(
                HashUtils.chunkHash("aaa", "src/doc.md", 0),
                HashUtils.chunkHash("bbb", "src/doc.md", 0));
    }

    @Test
    void chunkHash_different_source_different_hash() {
        assertNotEquals(
                HashUtils.chunkHash("content", "src/a.md", 0),
                HashUtils.chunkHash("content", "src/b.md", 0));
    }

    @Test
    void chunkHash_different_index_different_hash() {
        assertNotEquals(
                HashUtils.chunkHash("content", "src/doc.md", 0),
                HashUtils.chunkHash("content", "src/doc.md", 1));
    }

    @Test
    void chunkHash_null_inputs_should_not_throw() {
        String hash = HashUtils.chunkHash(null, null, 0);
        assertEquals(64, hash.length());
    }
}
