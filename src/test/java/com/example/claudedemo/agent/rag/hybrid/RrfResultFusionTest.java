package com.example.claudedemo.agent.rag.hybrid;

import com.example.claudedemo.agent.rag.RagDocument;
import com.example.claudedemo.agent.rag.RagSearchResult;
import com.example.claudedemo.agent.rag.RetrievalType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RrfResultFusion} 单元测试.
 *
 * @since 0.0.1
 */
class RrfResultFusionTest {

    private RrfResultFusion fusion;

    @BeforeEach
    void setUp() {
        fusion = new RrfResultFusion(60);
    }

    private static RagSearchResult kw(String id, double score, int rank) {
        return new RagSearchResult(id, "doc-" + id, "title-" + id,
                "content-" + id, "src/" + id + ".md",
                score, rank, RetrievalType.KEYWORD, Map.of());
    }

    private static RagSearchResult vec(String id, double score, int rank) {
        return new RagSearchResult(id, "doc-" + id, "title-" + id,
                "content-" + id, "src/" + id + ".md",
                score, rank, RetrievalType.VECTOR, Map.of());
    }

    @Test
    void fuse_keyword_only_returns_keyword_results() {
        List<RagSearchResult> kw = List.of(kw("a", 0.9, 1), kw("b", 0.7, 2));
        List<RagDocument> result = fusion.fuse(kw, List.of(), 3);
        assertEquals(2, result.size());
        assertEquals("a", result.get(0).id());
        assertEquals("b", result.get(1).id());
    }

    @Test
    void fuse_vector_only_returns_vector_results() {
        List<RagSearchResult> vec = List.of(vec("x", 0.95, 1), vec("y", 0.80, 2));
        List<RagDocument> result = fusion.fuse(List.of(), vec, 3);
        assertEquals(2, result.size());
    }

    @Test
    void fuse_merges_both_lists() {
        List<RagSearchResult> kw = List.of(kw("a", 0.9, 1));
        List<RagSearchResult> vec = List.of(vec("b", 0.8, 1));
        List<RagDocument> result = fusion.fuse(kw, vec, 5);
        assertEquals(2, result.size());
    }

    @Test
    void fuse_same_id_scores_are_additive() {
        // a 在两路都排第 1 → rrfScore = 1/61 + 1/61 = 2/61
        List<RagSearchResult> kw = List.of(kw("a", 0.9, 1));
        List<RagSearchResult> vec = List.of(vec("a", 0.95, 1));
        List<RagDocument> result = fusion.fuse(kw, vec, 3);
        assertEquals(1, result.size());
        assertTrue(result.get(0).score() > 0);
    }

    @Test
    void lower_rank_gets_higher_weight() {
        // a: rank=1 → rrf = 1/61 ≈ 0.0164
        // b: rank=5 → rrf = 1/65 ≈ 0.0154
        List<RagSearchResult> kw = List.of(kw("a", 0.8, 1), kw("b", 0.99, 5));
        List<RagDocument> result = fusion.fuse(kw, List.of(), 5);
        // a (rank=1) 的 fusedScore > b (rank=5),即使 b 原始分更高
        assertEquals("a", result.get(0).id());
    }

    @Test
    void topK_truncates_correctly() {
        List<RagSearchResult> kw = List.of(
                kw("a", 0.9, 1), kw("b", 0.7, 2), kw("c", 0.5, 3));
        List<RagDocument> result = fusion.fuse(kw, List.of(), 2);
        assertEquals(2, result.size());
    }

    @Test
    void fuse_empty_inputs_returns_empty() {
        assertTrue(fusion.fuse(List.of(), List.of(), 5).isEmpty());
        assertTrue(fusion.fuse(null, null, 5).isEmpty());
    }

    @Test
    void fuse_zero_topK_returns_empty() {
        List<RagSearchResult> kw = List.of(kw("a", 0.9, 1));
        assertTrue(fusion.fuse(kw, List.of(), 0).isEmpty());
    }

    @Test
    void metadata_contains_hybrid_info() {
        List<RagSearchResult> kw = List.of(kw("a", 0.9, 1));
        List<RagSearchResult> vec = List.of(vec("a", 0.95, 1));
        List<RagDocument> result = fusion.fuse(kw, vec, 3);
        assertEquals(1, result.size());
        Map<String, Object> meta = result.get(0).metadataView();
        assertTrue(meta.containsKey("retrievalType"));
        assertTrue(meta.containsKey("fusedScore"));
    }
}
