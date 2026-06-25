package com.example.claudedemo.agent.rag.store;

import com.example.claudedemo.agent.rag.embedding.EmbeddingVector;
import com.example.claudedemo.agent.rag.embedding.SimpleHashEmbeddingClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link InMemoryVectorStore} 单元测试.
 *
 * @since 0.0.1
 */
class InMemoryVectorStoreTest {

    private InMemoryVectorStore store;
    private SimpleHashEmbeddingClient embedder;

    @BeforeEach
    void setUp() {
        store = new InMemoryVectorStore();
        embedder = new SimpleHashEmbeddingClient(64);
    }

    private VectorDocument doc(String id, String content) {
        return new VectorDocument(id, content, "src/" + id + ".md",
                Map.of(), embedder.embed(content));
    }

    @Test
    void upsert_and_search_returns_doc() {
        store.upsert(doc("d1", "北京的用户"));
        store.upsert(doc("d2", "上海的用户"));

        EmbeddingVector query = embedder.embed("北京的用户");
        List<VectorSearchResult> results = store.search(query, 3);
        assertEquals(2, results.size());
        assertEquals("d1", results.get(0).document().id());
    }

    @Test
    void topK_truncates_correctly() {
        store.upsert(doc("d1", "a"));
        store.upsert(doc("d2", "b"));
        store.upsert(doc("d3", "c"));

        List<VectorSearchResult> results = store.search(embedder.embed("a"), 2);
        assertEquals(2, results.size());
    }

    @Test
    void empty_store_returns_empty() {
        assertTrue(store.search(embedder.embed("anything"), 3).isEmpty());
    }

    @Test
    void upsert_overwrites_same_id() {
        store.upsert(doc("key", "旧内容"));
        store.upsert(doc("key", "新内容"));

        assertEquals(1, store.size());
        VectorDocument found = store.search(embedder.embed("新内容"), 1).get(0).document();
        assertEquals("key", found.id());
        assertEquals("新内容", found.content());
    }

    @Test
    void exact_match_text_scores_highest() {
        store.upsert(doc("d1", "北京用户人口统计"));
        store.upsert(doc("d2", "上海城市人口数据"));

        // 精确匹配(embed("北京用户人口统计") == doc("d1").embedding()) → d1 应排第一
        List<VectorSearchResult> results = store.search(embedder.embed("北京用户人口统计"), 3);
        assertFalse(results.isEmpty());
        assertEquals("d1", results.get(0).document().id(),
                "精确匹配应与原文向量一致,score 应为最高,实际第一: " + results.get(0).document().id());
    }

    @Test
    void search_with_null_query_returns_empty() {
        store.upsert(doc("d1", "content"));
        assertTrue(store.search(null, 3).isEmpty());
    }

    @Test
    void search_with_non_positive_topK_returns_empty() {
        store.upsert(doc("d1", "content"));
        assertTrue(store.search(embedder.embed("content"), 0).isEmpty());
        assertTrue(store.search(embedder.embed("content"), -1).isEmpty());
    }

    @Test
    void upsert_null_document_is_noop() {
        store.upsert(null);
        assertTrue(store.isEmpty());
    }

    @Test
    void upsertAll_bulk_insert() {
        store.upsertAll(List.of(doc("a", "A"), doc("b", "B"), doc("c", "C")));
        assertEquals(3, store.size());
    }

    @Test
    void clear_empties_store() {
        store.upsert(doc("d1", "x"));
        store.upsert(doc("d2", "y"));
        assertFalse(store.isEmpty());
        store.clear();
        assertTrue(store.isEmpty());
    }

    @Test
    void size_tracks_document_count() {
        assertEquals(0, store.size());
        store.upsert(doc("a", "A"));
        assertEquals(1, store.size());
        store.upsert(doc("b", "B"));
        assertEquals(2, store.size());
        store.upsert(doc("a", "A2"));
        assertEquals(2, store.size()); // 覆盖
    }
}
