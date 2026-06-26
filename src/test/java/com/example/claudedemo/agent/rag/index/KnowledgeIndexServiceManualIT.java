package com.example.claudedemo.agent.rag.index;

import com.example.claudedemo.agent.rag.RagDocument;
import com.example.claudedemo.agent.rag.RagRetriever;
import com.example.claudedemo.agent.rag.store.VectorStore;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * {@link KnowledgeIndexService} 端到端手动集成测试.
 *
 * <p>验证全链路:
 * <ol>
 *   <li>启动 Spring 上下文(PgVectorStore)</li>
 *   <li>调用 {@code KnowledgeIndexService.rebuildAll()}</li>
 *   <li>打印 {@link IndexStats}</li>
 *   <li>确认 {@code VectorStore.count() > 0}</li>
 *   <li>通过 {@link RagRetriever} 检索确认召回</li>
 * </ol>
 *
 * <p><b>执行条件</b>:必须设置环境变量 {@code RUN_RAG_INDEX_IT=true},
 * 且本机 pgvector 已运行(host=localhost, port=5432, database=rag, user/pass=postgres).
 *
 * <p><b>运行方式</b>:
 * <pre>
 *   RUN_RAG_INDEX_IT=true mvn test -Dtest=KnowledgeIndexServiceManualIT -pl .
 * </pre>
 *
 * @since 0.0.1
 */
@SpringBootTest
@Tag("manual-it")
@TestPropertySource(properties = {
        "vector-store.provider=pgvector",
        "vector-store.table-name=rag_vectors",
        "vector-store.dimension=128",
        "vector-store.initialize-schema=true",
        "vector-store.pg.url=jdbc:postgresql://localhost:5432/rag",
        "vector-store.pg.username=postgres",
        "vector-store.pg.password=${PGVECTOR_PASSWORD:postgres}",
        "vector-store.pg.driver-class-name=org.postgresql.Driver",
        "embedding.provider=simple-hash",
        "embedding.dimension=128",
        "rag.retrieval-mode=vector",
        "rag.index.auto-index-on-startup=false",
        "rag.index.cleanup-removed-documents=true",
})
class KnowledgeIndexServiceManualIT {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIndexServiceManualIT.class);
    private static final String ENV_RUN = "RUN_RAG_INDEX_IT";

    private static boolean shouldRun;

    @Autowired
    private KnowledgeIndexService indexService;

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private RagRetriever ragRetriever;

    @BeforeAll
    static void checkEnv() {
        String runFlag = System.getenv(ENV_RUN);
        shouldRun = "true".equalsIgnoreCase(runFlag);
        if (!shouldRun) {
            log.info("跳过 KnowledgeIndexServiceManualIT: 环境变量 {} != true", ENV_RUN);
        } else {
            log.info("KnowledgeIndexServiceManualIT 环境变量已确认,开始端到端验证");
        }
    }

    @Test
    void rebuildAll_should_index_and_retrieve() {
        assumeTrue(shouldRun, "跳过: RUN_RAG_INDEX_IT != true");

        assertNotNull(indexService, "KnowledgeIndexService 应已由 Spring 注入");

        // 1. 全量重建索引
        IndexStats stats = indexService.rebuildAll();
        log.info("===========================================================");
        log.info("rebuildAll 完成: {}", stats);
        log.info("===========================================================");

        assertTrue(stats.documentCount() > 0, "应有文档被索引");
        assertTrue(stats.indexedChunkCount() > 0, "应有 chunk 被索引");

        // 2. 验证向量存储中有数据
        long count = vectorStore.count();
        log.info("VectorStore count: {}", count);
        assertTrue(count > 0, "pgvector 中应有向量数据");

        // 3. 检索验证
        String question = "查询有效用户来自哪些城市";
        List<RagDocument> results = ragRetriever.retrieve(question, 5);
        log.info("查询: {}", question);
        log.info("命中 {} 条", results.size());
        for (int i = 0; i < results.size(); i++) {
            RagDocument doc = results.get(i);
            String summary = doc.content();
            if (summary != null && summary.length() > 120) {
                summary = summary.substring(0, 120) + "...";
            }
            log.info("[{}] title={} source={} score={:.4f} content={}",
                    i + 1, doc.title(), doc.source(), doc.score(), summary);
        }
        assertFalse(results.isEmpty(), "应至少命中一条文档");
    }

    @Test
    void indexChangedDocuments_should_skip_unchanged() {
        assumeTrue(shouldRun, "跳过: RUN_RAG_INDEX_IT != true");

        // 先全量重建
        indexService.rebuildAll();

        // 二次增量索引,文档未变,应全部跳过
        IndexStats stats = indexService.indexChangedDocuments();
        log.info("indexChangedDocuments(未变): {}", stats);

        assertEquals(0, stats.indexedChunkCount());
        assertTrue(stats.skippedChunkCount() > 0, "应跳过所有未变 chunk");
    }
}
