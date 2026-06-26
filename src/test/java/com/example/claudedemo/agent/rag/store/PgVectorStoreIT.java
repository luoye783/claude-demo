package com.example.claudedemo.agent.rag.store;

import com.example.claudedemo.agent.rag.embedding.EmbeddingProperties;
import com.example.claudedemo.agent.rag.embedding.EmbeddingVector;
import com.example.claudedemo.agent.rag.embedding.SimpleHashEmbeddingClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PgVectorStore} 手动集成测试.
 *
 * <p>使用 {@code pgvector/pgvector:pg17} Docker 容器,验证真实 upsert + search 流程。
 * 只在 {@code RUN_PGVECTOR_IT=true} 时执行。
 *
 * @since 0.0.1
 */
@Tag("manual-it")
class PgVectorStoreIT {

    private static final Logger log = LoggerFactory.getLogger(PgVectorStoreIT.class);
    private static final String ENV_RUN = "RUN_PGVECTOR_IT";

    private static PgVectorStore store;
    private static SimpleHashEmbeddingClient embedder;
    private static boolean shouldRun = false;

    @BeforeAll
    static void setUp() {
        String runFlag = System.getenv(ENV_RUN);
        if (!"true".equalsIgnoreCase(runFlag)) {
            log.info("跳过 PgVectorStore IT: {} != true", ENV_RUN);
            shouldRun = false;
            return;
        }

        int dimension = 4;
        try (PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("pgvector/pgvector:pg17")) {
            pg.start();
            JdbcTemplate jdbc = new JdbcTemplate(
                    new org.springframework.jdbc.datasource.SingleConnectionDataSource(
                            pg.getJdbcUrl(), pg.getUsername(), pg.getPassword(), true));

            VectorStoreProperties vp = new VectorStoreProperties();
            vp.setProvider(VectorStoreProvider.PGVECTOR);
            vp.setTableName("test_vectors");
            vp.setDimension(dimension);
            vp.setInitializeSchema(true);

            EmbeddingProperties ep = new EmbeddingProperties();
            ep.setDimension(dimension);

            store = new PgVectorStore(jdbc, vp, ep);
            embedder = new SimpleHashEmbeddingClient(dimension);
            shouldRun = true;
            log.info("PgVectorStore IT 容器已就绪: jdbcUrl={}", pg.getJdbcUrl());
        }
    }

    private VectorDocument doc(String id, String content) {
        return new VectorDocument(id, content, "src/" + id + ".md",
                Map.of(), embedder.embed(content));
    }

    @Test
    void it_should_upsert_and_search() {
        if (!shouldRun) return;
        store.upsert(doc("d1", "北京人口数据"));
        store.upsert(doc("d2", "上海经济统计"));

        // 用 d1 原文查询,最近的结果应为 d1
        List<VectorSearchResult> results = store.search(embedder.embed("北京人口数据"), 5);
        assertFalse(results.isEmpty());
        assertEquals("d1", results.get(0).document().id());
    }

    @Test
    void it_should_return_topK() {
        if (!shouldRun) return;
        store.upsert(doc("a", "aaa"));
        store.upsert(doc("b", "bbb"));
        store.upsert(doc("c", "ccc"));

        List<VectorSearchResult> results = store.search(embedder.embed("aaa"), 2);
        assertEquals(2, results.size());
    }

    @Test
    void it_should_upsert_overwrites_same_id() {
        if (!shouldRun) return;
        store.upsert(doc("dup", "原始内容"));
        store.upsert(doc("dup", "新内容"));

        List<VectorSearchResult> results = store.search(embedder.embed("新内容"), 3);
        assertFalse(results.isEmpty());
        assertEquals("dup", results.get(0).document().id());
        assertEquals("新内容", results.get(0).document().content());
    }

    @Test
    void it_should_score_similar_text_higher() {
        if (!shouldRun) return;
        store.upsert(doc("sim", "北京用户人口统计数据"));
        store.upsert(doc("dif", "上海城市经济数据分析"));

        // 查询精确匹配 sim 的文本,sim 应排第一
        List<VectorSearchResult> results = store.search(embedder.embed("北京用户人口统计数据"), 3);
        assertFalse(results.isEmpty());
        assertEquals("sim", results.get(0).document().id());
        assertTrue(results.get(0).score() > 0, "相似度应 > 0");
    }
}
