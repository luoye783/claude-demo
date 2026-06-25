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

    private final InMemoryRagRetriever retriever =
            new InMemoryRagRetriever(InMemoryRagRetriever.defaultDocuments());

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

    // ==================== V2 chunk 构造器 ====================

    @Test
    void should_retrieve_from_chunk_source() {
        // 模拟 loader 返回一个 doc,chunker 切为 2 个 chunk
        KnowledgeDocument doc = new KnowledgeDocument("users", "users 表",
                "users 表存储用户基础信息,包含 city、status。",
                "knowledge-base/users.md", java.util.Map.of());
        KnowledgeDocumentLoader loader = () -> java.util.List.of(doc);
        TextChunker chunker = d -> {
            return java.util.List.of(
                    new KnowledgeChunk("users-chunk-0", "users", "users 表",
                            "users 表存储用户基础信息,包含 city、status。",
                            "knowledge-base/users.md", 0, java.util.Map.of())
            );
        };
        RagProperties props = new RagProperties();
        InMemoryRagRetriever chunked = new InMemoryRagRetriever(loader, chunker, props);

        // users → 命中 (content 含 "users")
        List<RagDocument> docs = chunked.retrieve("users", 3);
        assertFalse(docs.isEmpty(), "chunk 源应能命中关键词");
        assertTrue(docs.get(0).content().contains("users 表"));

        // status → 命中 (content 含 "status")
        List<RagDocument> docs2 = chunked.retrieve("status", 3);
        assertFalse(docs2.isEmpty());
        assertTrue(docs2.get(0).content().contains("status"));
    }

    @Test
    void should_return_empty_when_loader_returns_empty() {
        KnowledgeDocumentLoader emptyLoader = () -> java.util.List.of();
        TextChunker chunker = d -> java.util.List.of(
                new KnowledgeChunk("c-0", "u", "t", "x", "s", 0, java.util.Map.of()));
        InMemoryRagRetriever emptyRetriever = new InMemoryRagRetriever(emptyLoader, chunker, new RagProperties());
        assertTrue(emptyRetriever.retrieve("anything", 3).isEmpty());
    }

    @Test
    void should_return_empty_when_loader_fails() {
        KnowledgeDocumentLoader failing = () -> { throw new RuntimeException("fail"); };
        InMemoryRagRetriever ret = new InMemoryRagRetriever(failing, new TextChunker() {
            @Override public java.util.List<KnowledgeChunk> chunk(KnowledgeDocument d) {
                return java.util.List.of();
            }
        }, new RagProperties());
        assertTrue(ret.retrieve("anything", 3).isEmpty());
    }

    @Test
    void should_properly_initialize_chunk_source_counts() {
        KnowledgeDocument doc = new KnowledgeDocument("test", "Test",
                "a b c d e f g h i j k l m n o p", "s", java.util.Map.of());
        TextChunker chunker = d -> {
            int chunkSize = 5;
            int total = d.content().length();
            int count = (total + chunkSize - 1) / chunkSize;
            java.util.List<KnowledgeChunk> list = new java.util.ArrayList<>();
            for (int i = 0; i < count; i++) {
                int end = Math.min((i + 1) * chunkSize, total);
                list.add(new KnowledgeChunk("t-chunk-" + i, "test", "Test",
                        d.content().substring(i * chunkSize, end), "s", i, java.util.Map.of()));
            }
            return list;
        };
        KnowledgeDocumentLoader loader = () -> java.util.List.of(doc);
        InMemoryRagRetriever ret = new InMemoryRagRetriever(loader, chunker, new RagProperties());
        // 31 字符(含空格)/5 ≈ 7 chunk entries
        assertEquals(7, ret.entryCount(), "31 字符 / chunkSize=5 应有 7 个 entry");
    }

    @Test
    void should_use_empty_constructor_for_empty_corpus() {
        InMemoryRagRetriever empty = new InMemoryRagRetriever();
        assertTrue(empty.retrieve("anything", 3).isEmpty());
    }

    // ==================== V3 向量模式 ====================

    @Test
    void vector_mode_with_real_store_returns_results() {
        KnowledgeDocumentLoader loader = () -> java.util.List.of(
                new KnowledgeDocument("users", "users 表",
                        "users 表存储用户基础信息", "kb/users.md", java.util.Map.of()));
        TextChunker chunker = d -> java.util.List.of(
                new KnowledgeChunk("u-c-0", "users", "users 表",
                        "users 表存储用户基础信息", "kb/users.md", 0, java.util.Map.of()));
        RagProperties vecProps = new RagProperties();
        vecProps.setRetrievalMode(RetrievalMode.VECTOR);
        vecProps.setEmbeddingDimension(32);

        var embedder = new com.example.claudedemo.agent.rag.embedding.SimpleHashEmbeddingClient(32);
        var vecStore = new com.example.claudedemo.agent.rag.store.InMemoryVectorStore();

        InMemoryRagRetriever retriever = new InMemoryRagRetriever(loader, chunker, vecProps, embedder, vecStore);
        List<RagDocument> docs = retriever.retrieve("users 表", 3);
        // 精确匹配应返回结果(同一 chunk 文本)
        assertFalse(docs.isEmpty(), "vector 模式应能检索到结果");
        assertTrue(docs.get(0).content().contains("users 表"));
    }

    @Test
    void vector_mode_empty_store_returns_empty() {
        RagProperties vecProps = new RagProperties();
        vecProps.setRetrievalMode(RetrievalMode.VECTOR);

        // 用空 loader → 没有文档 upsert → vectorStore 空 → 无结果
        KnowledgeDocumentLoader emptyLoader = () -> java.util.List.of();
        TextChunker chunker = d -> java.util.List.of();
        var embedder = new com.example.claudedemo.agent.rag.embedding.SimpleHashEmbeddingClient(8);
        var vecStore = new com.example.claudedemo.agent.rag.store.InMemoryVectorStore();

        InMemoryRagRetriever retriever = new InMemoryRagRetriever(emptyLoader, chunker, vecProps, embedder, vecStore);
        assertTrue(retriever.retrieve("anything", 3).isEmpty());
    }

    @Test
    void vector_mode_degrades_to_keyword_when_no_embedder() {
        RagProperties vecProps = new RagProperties();
        vecProps.setRetrievalMode(RetrievalMode.VECTOR);

        // 传 VECTOR mode 但 embedder=null → 应退化到关键词,返回空(空语料)
        InMemoryRagRetriever retriever = new InMemoryRagRetriever(null, null, vecProps, null, null);
        assertTrue(retriever.retrieve("anything", 3).isEmpty());
    }

    @Test
    void keyword_mode_by_default() {
        // V1 构造器: 默认 KEYWORD
        InMemoryRagRetriever r1 = new InMemoryRagRetriever();
        // V2 构造器: 默认 KEYWORD
        InMemoryRagRetriever r2 = new InMemoryRagRetriever(InMemoryRagRetriever.defaultDocuments());

        assertTrue(r1.retrieve("anything", 3).isEmpty());
        List<RagDocument> docs = r2.retrieve("users", 3);
        assertFalse(docs.isEmpty(), "KEYWORD 模式应返回 defaultDocuments 中的结果");
    }

    @Test
    void keyword_mode_with_vector_components_still_keyword() {
        // 传了 embedder+store 但 mode=KEYWORD → 走关键词
        RagProperties props = new RagProperties(); // KEYWORD default
        var embedder = new com.example.claudedemo.agent.rag.embedding.SimpleHashEmbeddingClient(16);
        var vecStore = new com.example.claudedemo.agent.rag.store.InMemoryVectorStore();

        KnowledgeDocumentLoader loader = () -> java.util.List.of(
                new KnowledgeDocument("test", "Test",
                        "上海市人口统计数据显示", "s.md", java.util.Map.of()));
        TextChunker chunker = d -> java.util.List.of(
                new KnowledgeChunk("t-c-0", "test", "Test",
                        "上海市人口统计数据显示", "s.md", 0, java.util.Map.of()));

        InMemoryRagRetriever retriever = new InMemoryRagRetriever(loader, chunker, props, embedder, vecStore);
        // 关键词检索应能通过 "上海" 命中 "上海市人口统计数据显示"
        List<RagDocument> docs = retriever.retrieve("上海", 3);
        assertFalse(docs.isEmpty(), "KEYWORD 模式即使传了 vector 组件也应走关键词");
    }
}
