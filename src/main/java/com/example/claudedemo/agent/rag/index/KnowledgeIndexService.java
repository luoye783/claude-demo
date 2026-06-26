package com.example.claudedemo.agent.rag.index;

import com.example.claudedemo.agent.rag.KnowledgeChunk;
import com.example.claudedemo.agent.rag.KnowledgeDocument;
import com.example.claudedemo.agent.rag.KnowledgeDocumentLoader;
import com.example.claudedemo.agent.rag.TextChunker;
import com.example.claudedemo.agent.rag.embedding.EmbeddingClient;
import com.example.claudedemo.agent.rag.embedding.EmbeddingVector;
import com.example.claudedemo.agent.rag.store.VectorDocument;
import com.example.claudedemo.agent.rag.store.VectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 知识库索引生命周期管理(V2 第十一阶段 RAG V6).
 *
 * <p>职责:
 * <ul>
 *   <li>{@link #rebuildAll()} — 清空并重建全部索引</li>
 *   <li>{@link #indexChangedDocuments()} — 增量索引,只对变化的 chunk 重新 embedding</li>
 *   <li>{@link #deleteRemovedDocuments(Set)} — 清理已删除文档的向量</li>
 *   <li>{@link #getIndexStats()} — 即时统计</li>
 * </ul>
 *
 * <p><b>增量判断</b>:基于 chunk 的 SHA-256 哈希({@link HashUtils#chunkHash}),
 * 通过 {@link VectorStore#findById} 读取已存储 chunk 的 chunkHash,
 * 相同则跳过 embedding,不同则重新生成向量并 upsert。
 *
 * <p><b>调用方</b>:本服务属于 RAG 基础设施,由开发者/运维手动调用;
 * Agent 不感知索引生命周期。
 *
 * @since 0.0.1
 */
public class KnowledgeIndexService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIndexService.class);

    private final KnowledgeDocumentLoader loader;
    private final TextChunker chunker;
    private final EmbeddingClient embeddingClient;
    private final VectorStore vectorStore;
    private final IndexProperties props;

    public KnowledgeIndexService(KnowledgeDocumentLoader loader,
                                  TextChunker chunker,
                                  EmbeddingClient embeddingClient,
                                  VectorStore vectorStore,
                                  IndexProperties props) {
        this.loader = loader;
        this.chunker = chunker;
        this.embeddingClient = embeddingClient;
        this.vectorStore = vectorStore;
        this.props = props;
    }

    /**
     * 全量重建索引:清空向量表 → 重新加载 → chunk → embed → 全量 upsert.
     *
     * @return 索引统计
     */
    public IndexStats rebuildAll() {
        long start = System.currentTimeMillis();
        log.info("rebuildAll: 开始全量重建索引");

        vectorStore.clear();
        List<KnowledgeDocument> documents = loadDocuments();
        int totalChunks = 0;

        for (KnowledgeDocument doc : documents) {
            List<KnowledgeChunk> chunks = chunker.chunk(doc);
            for (KnowledgeChunk chunk : chunks) {
                String chunkHash = HashUtils.chunkHash(chunk.content(), chunk.source(), chunk.chunkIndex());
                EmbeddingVector vec = embeddingClient.embed(chunk.content());
                VectorDocument vd = new VectorDocument(
                        chunk.id(), chunk.documentId(), chunk.content(), chunk.source(),
                        chunkHash, chunk.metadataView(), vec);
                vectorStore.upsert(vd);
                totalChunks++;
            }
        }

        long durationMs = System.currentTimeMillis() - start;
        IndexStats stats = new IndexStats(documents.size(), totalChunks, totalChunks, 0, 0, durationMs);
        log.info("rebuildAll: 完成, {}", stats);
        return stats;
    }

    /**
     * 增量索引:仅对内容变化的 chunk 重新 embedding 和 upsert.
     *
     * <p>流程:
     * <ol>
     *   <li>加载知识库文档</li>
     *   <li>对每个 chunk 计算 chunkHash</li>
     *   <li>通过 {@code vectorStore.findById} 读取旧 chunkHash</li>
     *   <li>相同 → 跳过;不同或不存在 → embed + upsert</li>
     *   <li>根据配置清理已删除文档的向量</li>
     * </ol>
     *
     * @return 索引统计
     */
    public IndexStats indexChangedDocuments() {
        long start = System.currentTimeMillis();
        log.info("indexChangedDocuments: 开始增量索引");

        List<KnowledgeDocument> documents = loadDocuments();
        int indexedCount = 0;
        int skippedCount = 0;
        int totalChunks = 0;

        for (KnowledgeDocument doc : documents) {
            List<KnowledgeChunk> chunks = chunker.chunk(doc);
            for (KnowledgeChunk chunk : chunks) {
                totalChunks++;
                String newChunkHash = HashUtils.chunkHash(chunk.content(), chunk.source(), chunk.chunkIndex());

                Optional<VectorDocument> oldDoc = vectorStore.findById(chunk.id());
                if (oldDoc.isPresent() && newChunkHash.equals(oldDoc.get().chunkHash())) {
                    skippedCount++;
                    continue;
                }

                EmbeddingVector vec = embeddingClient.embed(chunk.content());
                VectorDocument vd = new VectorDocument(
                        chunk.id(), chunk.documentId(), chunk.content(), chunk.source(),
                        newChunkHash, chunk.metadataView(), vec);
                vectorStore.upsert(vd);
                indexedCount++;
            }
        }

        // 清理已删除文档
        int deletedCount = 0;
        if (props.isCleanupRemovedDocuments()) {
            Set<String> currentDocIds = documents.stream()
                    .map(KnowledgeDocument::id)
                    .collect(Collectors.toSet());
            deletedCount = deleteRemovedDocuments(currentDocIds).deletedChunkCount();
        }

        long durationMs = System.currentTimeMillis() - start;
        IndexStats stats = new IndexStats(
                documents.size(), totalChunks, indexedCount, skippedCount, deletedCount, durationMs);
        log.info("indexChangedDocuments: 完成, {}", stats);
        return stats;
    }

    /**
     * 清理已删除文档的向量.
     *
     * <p>比较本地知识库文档 ID 集合与 VectorStore 中已索引的 documentId 集合,
     * 删除数据库中存在但本地不存在的文档的所有向量。
     *
     * @param currentDocumentIds 当前知识库文档 ID 集合(调用方传入,避免重复加载)
     * @return 索引统计(仅 deletedChunkCount 有意义)
     */
    public IndexStats deleteRemovedDocuments(Set<String> currentDocumentIds) {
        long start = System.currentTimeMillis();
        Set<String> storedDocIds = vectorStore.getDocumentIds();
        int deletedCount = 0;

        for (String storedId : storedDocIds) {
            if (!currentDocumentIds.contains(storedId)) {
                vectorStore.deleteByDocumentId(storedId);
                deletedCount++;
                log.info("deleteRemovedDocuments: 清理已删除文档 {}", storedId);
            }
        }

        long durationMs = System.currentTimeMillis() - start;
        IndexStats stats = new IndexStats(0, 0, 0, 0, deletedCount, durationMs);
        if (deletedCount > 0) {
            log.info("deleteRemovedDocuments: 完成, {}", stats);
        }
        return stats;
    }

    /**
     * 即时获取索引统计信息.
     */
    public IndexStats getIndexStats() {
        List<KnowledgeDocument> documents = loadDocuments();
        int chunkCount = 0;
        for (KnowledgeDocument doc : documents) {
            chunkCount += chunker.chunk(doc).size();
        }
        long storedCount = vectorStore.count();
        return new IndexStats(documents.size(), chunkCount,
                (int) storedCount, 0, 0, 0);
    }

    // ==================== 内部 ====================

    private List<KnowledgeDocument> loadDocuments() {
        try {
            List<KnowledgeDocument> docs = loader.load();
            return docs != null ? docs : List.of();
        } catch (Exception e) {
            log.warn("知识库文档加载失败: {}", e.getMessage());
            return List.of();
        }
    }
}
