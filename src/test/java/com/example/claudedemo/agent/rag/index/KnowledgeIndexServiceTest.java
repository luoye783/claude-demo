package com.example.claudedemo.agent.rag.index;

import com.example.claudedemo.agent.rag.KnowledgeChunk;
import com.example.claudedemo.agent.rag.KnowledgeDocument;
import com.example.claudedemo.agent.rag.KnowledgeDocumentLoader;
import com.example.claudedemo.agent.rag.TextChunker;
import com.example.claudedemo.agent.rag.embedding.EmbeddingClient;
import com.example.claudedemo.agent.rag.embedding.EmbeddingVector;
import com.example.claudedemo.agent.rag.store.InMemoryVectorStore;
import com.example.claudedemo.agent.rag.store.VectorDocument;
import com.example.claudedemo.agent.rag.store.VectorStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link KnowledgeIndexService} 单元测试.
 *
 * <p>使用真实的 {@link InMemoryVectorStore} 和 mock 组件,不启动 Spring 上下文。
 *
 * @since 0.0.1
 */
class KnowledgeIndexServiceTest {

    private KnowledgeIndexService service;
    private VectorStore vectorStore;
    private DocumentLoaderStub loader;
    private TextChunkerStub chunker;
    private EmbeddingClientStub embeddingClient;
    private IndexProperties props;

    @BeforeEach
    void setUp() {
        vectorStore = new InMemoryVectorStore();
        loader = new DocumentLoaderStub();
        chunker = new TextChunkerStub();
        embeddingClient = new EmbeddingClientStub();
        props = new IndexProperties();
        props.setCleanupRemovedDocuments(true);
        service = new KnowledgeIndexService(loader, chunker, embeddingClient, vectorStore, props);
    }

    @Test
    void rebuildAll_should_clear_and_rebuild() {
        // 先写入一些旧数据
        vectorStore.upsert(new VectorDocument("old-chunk-0", "old-doc", "old", "old.md",
                null, Map.of(), new EmbeddingVector(new float[]{1f}, 1)));
        assertEquals(1, vectorStore.count());

        IndexStats stats = service.rebuildAll();

        // 重建后应只有当前文档的 chunk
        assertEquals(1, stats.documentCount());
        assertEquals(2, stats.chunkCount());  // docA → 2 chunks
        assertEquals(2, stats.indexedChunkCount());
        assertEquals(2, vectorStore.count());
    }

    @Test
    void indexChangedDocuments_should_skip_unchanged_chunks() {
        // 初次索引
        IndexStats first = service.indexChangedDocuments();
        assertEquals(2, first.indexedChunkCount());
        assertEquals(0, first.skippedChunkCount());

        // 二次索引,文档未变,全部跳过
        IndexStats second = service.indexChangedDocuments();
        assertEquals(0, second.indexedChunkCount());
        assertEquals(2, second.skippedChunkCount());
    }

    @Test
    void indexChangedDocuments_should_reindex_changed_chunks() {
        // 初次索引
        service.indexChangedDocuments();
        assertEquals(2, vectorStore.count());

        // 修改 loader 返回的文档内容
        loader.setContent("新内容");
        IndexStats stats = service.indexChangedDocuments();

        // chunk hash 已变,应重新索引
        assertEquals(2, stats.indexedChunkCount());
        assertEquals(0, stats.skippedChunkCount());
    }

    @Test
    void deleteRemovedDocuments_should_cleanup_orphaned_vectors() {
        // 先执行增量索引,写入 docA 的 2 个 chunk
        service.indexChangedDocuments();
        assertEquals(2, vectorStore.count());

        // 手写一个孤立的 orphan-doc 向量(不在当前知识库中)
        VectorDocument orphan = new VectorDocument("orphan-chunk-0", "orphan-doc",
                "orphan", "orphan.md", null, Map.of(),
                new EmbeddingVector(new float[]{1f}, 1));
        vectorStore.upsert(orphan);
        assertEquals(3, vectorStore.count()); // 2(docA) + 1(orphan)

        // 当前本地只有 docA, orphan-doc 应被清理
        IndexStats stats = service.deleteRemovedDocuments(Set.of("docA"));
        assertTrue(stats.deletedChunkCount() > 0);
        assertEquals(2, vectorStore.count()); // only docA chunks remain
    }

    @Test
    void getIndexStats_should_return_counts() {
        service.indexChangedDocuments();
        IndexStats stats = service.getIndexStats();
        assertEquals(1, stats.documentCount());
        assertEquals(2, stats.chunkCount());
    }

    // ==================== stub ====================

    static class DocumentLoaderStub implements KnowledgeDocumentLoader {
        private String content = "原始内容";

        void setContent(String content) {
            this.content = content;
        }

        @Override
        public List<KnowledgeDocument> load() {
            return List.of(new KnowledgeDocument("docA", "Doc A", content,
                    "knowledge-base/docA.md", Map.of()));
        }
    }

    static class TextChunkerStub implements TextChunker {
        @Override
        public List<KnowledgeChunk> chunk(KnowledgeDocument document) {
            return List.of(
                    new KnowledgeChunk("docA-chunk-0", "docA", document.title(),
                            document.content().substring(0, Math.min(2, document.content().length())),
                            document.source(), 0, Map.of()),
                    new KnowledgeChunk("docA-chunk-1", "docA", document.title(),
                            document.content().substring(Math.min(2, document.content().length())),
                            document.source(), 1, Map.of())
            );
        }
    }

    static class EmbeddingClientStub implements EmbeddingClient {
        @Override
        public EmbeddingVector embed(String text) {
            return new EmbeddingVector(new float[]{1f, 2f, 3f, 4f}, 4);
        }

        @Override
        public List<EmbeddingVector> embedAll(List<String> texts) {
            return texts.stream().map(this::embed).toList();
        }
    }
}
