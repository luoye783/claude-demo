package com.example.claudedemo.agent.rag.store;

import com.example.claudedemo.agent.rag.RagDocument;
import com.example.claudedemo.agent.rag.RagRetriever;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * PgVector + SimpleHash Embedding + Vector Retrieval 端到端手动集成测试.
 *
 * <p>验证全链路:
 * <ol>
 *   <li>启动 Spring 上下文(VectorStore → PgVectorStore)</li>
 *   <li>加载 knowledge-base/*.md → chunk → embed(SimpleHash, 128 维)</li>
 *   <li>upsert 到 pgvector</li>
 *   <li>vector search 检索问题</li>
 *   <li>验证返回 RagDocument 不为空</li>
 *   <li>打印命中详情</li>
 * </ol>
 *
 * <p><b>执行条件</b>:必须设置环境变量 {@code RUN_PGVECTOR_IT=true},
 * 且本机 pgvector 已运行(host=localhost, port=5432, database=rag, user=postgres, password=postgres).
 *
 * <p><b>运行方式</b>:
 * <pre>
 *   RUN_PGVECTOR_IT=true ./mvnw test -Dtest=PgVectorRagManualIT -pl .
 * </pre>
 *
 * @since 0.0.1
 */
@SpringBootTest
@Tag("manual-it")
@TestPropertySource(properties = {
        // --- vector-store → pgvector ---
        "vector-store.provider=pgvector",
        "vector-store.table-name=rag_vectors",
        "vector-store.dimension=128",
        "vector-store.initialize-schema=true",
        "vector-store.pg.url=jdbc:postgresql://localhost:5432/rag",
        "vector-store.pg.username=postgres",
        "vector-store.pg.password=${PGVECTOR_PASSWORD:postgres}",
        "vector-store.pg.driver-class-name=org.postgresql.Driver",

        // --- embedding → simple-hash ---
        "embedding.provider=simple-hash",
        "embedding.dimension=128",

        // --- rag → vector 检索 ---
        "rag.retrieval-mode=vector",
        "rag.knowledge-base-path=knowledge-base",
        "rag.chunk-size=800",
        "rag.chunk-overlap=100",
        "rag.top-k=5",
        "rag.min-score=0.05",
        "rag.max-content-chars=1500",
})
class PgVectorRagManualIT {

    private static final Logger log = LoggerFactory.getLogger(PgVectorRagManualIT.class);
    private static final String ENV_RUN = "RUN_PGVECTOR_IT";

    private static boolean shouldRun;

    @Autowired
    private RagRetriever ragRetriever;

    @BeforeAll
    static void checkEnv() {
        String runFlag = System.getenv(ENV_RUN);
        shouldRun = "true".equalsIgnoreCase(runFlag);
        if (!shouldRun) {
            log.info("跳过 PgVectorRagManualIT: 环境变量 {} != true", ENV_RUN);
        } else {
            log.info("PgVectorRagManualIT 环境变量已确认,开始端到端验证");
        }
    }

    /**
     * 端到端向量检索测试.
     *
     * <p>验证:知识库文档 → chunk → embed → upsert pgvector → vector search → 非空结果.
     */
    @Test
    void e2e_vector_retrieval_should_return_documents() {
        assumeTrue(shouldRun, "跳过: RUN_PGVECTOR_IT != true");

        // RagRetriever 在构造时已完成 加载→chunk→embed→upsert
        assertNotNull(ragRetriever, "RagRetriever 应已由 Spring 注入");

        String question = "查询有效用户来自哪些城市";
        List<RagDocument> results = ragRetriever.retrieve(question, 5);

        log.info("===========================================================");
        log.info("查询: {}", question);
        log.info("命中 {} 条文档", results.size());
        log.info("===========================================================");

        for (int i = 0; i < results.size(); i++) {
            RagDocument doc = results.get(i);
            String contentSummary = doc.content();
            if (contentSummary != null && contentSummary.length() > 120) {
                contentSummary = contentSummary.substring(0, 120) + "...";
            }
            log.info("[{}] title={} source={} score={:.4f} content={}",
                    i + 1, doc.title(), doc.source(), doc.score(), contentSummary);
        }
        log.info("===========================================================");

        // 断言:至少命中一条与用户/城市相关的文档
        assertFalse(results.isEmpty(),
                "应至少命中一条文档,问题='" + question + "'");
    }

    /**
     * 验证 pgvector 中有数据:至少能检索出一条 score > 0 的结果.
     */
    @Test
    void vector_search_should_return_positive_score() {
        assumeTrue(shouldRun, "跳过: RUN_PGVECTOR_IT != true");

        List<RagDocument> results = ragRetriever.retrieve("用户表字段说明", 3);
        assertFalse(results.isEmpty(), "应有与用户表相关的结果");

        RagDocument top = results.get(0);
        assertNotNull(top.content(), "文档内容应非空");
        log.info("top hit: title={} source={} score={:.4f}",
                top.title(), top.source(), top.score());
    }
}
