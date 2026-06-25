package com.example.claudedemo.agent.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link InMemoryRagRetriever} 单元测试.
 *
 * <p>覆盖: 输入校验、关键词匹配、score 排序、topK 截断、空查询、无匹配。
 *
 * @since 0.0.1
 */
class InMemoryRagRetrieverTest {

    private final InMemoryRagRetriever retriever = new InMemoryRagRetriever();

    // ==================== 输入校验 ====================

    @Test
    void should_reject_null_question() {
        assertThrows(IllegalArgumentException.class,
                () -> retriever.retrieve(null, 3));
    }

    @Test
    void should_reject_blank_question() {
        assertThrows(IllegalArgumentException.class,
                () -> retriever.retrieve("   ", 3));
    }

    @Test
    void should_reject_non_positive_topK() {
        assertThrows(IllegalArgumentException.class,
                () -> retriever.retrieve("users", 0));
        assertThrows(IllegalArgumentException.class,
                () -> retriever.retrieve("users", -1));
    }

    // ==================== 匹配行为 ====================

    @Test
    void should_return_empty_for_no_match() {
        // 纯英文 + 不在种子文档中的随机词
        List<RagDocument> docs = retriever.retrieve("completely unrelated content xyzqqq", 3);
        assertNotNull(docs);
        assertTrue(docs.isEmpty(), "无匹配关键词时应返回空列表");
    }

    @Test
    void should_match_single_keyword_in_default_docs() {
        List<RagDocument> docs = retriever.retrieve("users", 5);
        assertFalse(docs.isEmpty());
        // 至少 doc-users-table 应被命中(users 是其关键词)
        assertTrue(docs.stream().anyMatch(d -> "doc-users-table".equals(d.id())));
    }

    @Test
    void should_match_multiple_keywords_and_return_higher_score_first() {
        List<RagDocument> docs = retriever.retrieve("users city 城市", 5);
        assertFalse(docs.isEmpty());
        // 所有返回的文档 score 应 > 0
        for (RagDocument d : docs) {
            assertTrue(d.score() > 0.0, "命中文档 score 应 > 0: " + d);
        }
        // 列表应按 score 降序
        for (int i = 1; i < docs.size(); i++) {
            assertTrue(docs.get(i - 1).score() >= docs.get(i).score(),
                    "应按 score 降序排列");
        }
    }

    @Test
    void should_truncate_to_topK() {
        List<RagDocument> docs = retriever.retrieve("users", 1);
        assertEquals(1, docs.size(), "topK=1 应只返回 1 条");
    }

    @Test
    void should_return_at_most_default_size_when_topK_exceeds() {
        List<RagDocument> docs = retriever.retrieve("users", 100);
        assertTrue(docs.size() <= 5, "不超过种子文档总数 5");
    }

    @Test
    void should_not_modify_original_documents_score() {
        // 检索应返回新对象,不应修改原 list 中的 score
        List<RagDocument> before = InMemoryRagRetriever.defaultDocuments();
        double firstScore = before.get(0).score();
        retriever.retrieve("users city 城市", 3);
        // 原始列表应未被修改
        assertEquals(firstScore, before.get(0).score());
    }

    @Test
    void should_handle_case_insensitive_match() {
        // 小写化匹配:USERS / Users / users 都应命中
        List<RagDocument> lower = retriever.retrieve("users", 5);
        List<RagDocument> upper = retriever.retrieve("USERS", 5);
        assertEquals(lower.size(), upper.size(), "大小写应不敏感");
    }

    @Test
    void should_handle_chinese_keywords() {
        // 中文关键词 "用户" / "城市" / "删除" 应被命中(虽然分词是单字)
        List<RagDocument> docs = retriever.retrieve("用户城市删除", 5);
        assertFalse(docs.isEmpty(), "中文关键词应能命中默认文档");
    }

    @Test
    void should_return_empty_for_empty_default_corpus() {
        InMemoryRagRetriever empty = new InMemoryRagRetriever(List.of());
        List<RagDocument> docs = empty.retrieve("users", 3);
        assertTrue(docs.isEmpty());
    }

    @Test
    void should_handle_null_documents_constructor() {
        // null 文档列表也允许(空语料)
        InMemoryRagRetriever nullDocs = new InMemoryRagRetriever(null);
        List<RagDocument> docs = nullDocs.retrieve("users", 3);
        assertTrue(docs.isEmpty());
    }

    @Test
    void should_not_duplicate_same_document() {
        // 同一文档不应被多次返回(每个 doc 只遍历一次,无重复)
        List<RagDocument> docs = retriever.retrieve("users users users", 5);
        long distinctIds = docs.stream().map(RagDocument::id).distinct().count();
        assertEquals(docs.size(), distinctIds, "同一 doc id 不应重复");
    }

    // ==================== 默认文档结构 ====================

    @Test
    void should_have_non_empty_default_documents() {
        List<RagDocument> defaults = InMemoryRagRetriever.defaultDocuments();
        assertFalse(defaults.isEmpty());
        for (RagDocument d : defaults) {
            assertNotNull(d.id());
            assertNotNull(d.title());
            assertNotNull(d.content());
            assertNotNull(d.source());
            assertFalse(d.keywords().isEmpty(), "默认文档应带 keywords 用于 V1 匹配");
        }
    }
}
