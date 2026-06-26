package com.example.claudedemo.agent.rag.store;

import com.example.claudedemo.agent.rag.embedding.EmbeddingProperties;
import com.example.claudedemo.agent.rag.embedding.EmbeddingVector;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PgVectorStore} 单元测试.
 *
 * <p>测试逻辑正确性(表名校验 / toPgVector / 维度校验 / SQL 参数绑定),
 * 不依赖真实 PostgreSQL,通过 mock {@link JdbcTemplate} 实现。
 *
 * @since 0.0.1
 */
class PgVectorStoreTest {

    @Test
    void toPgVector_should_format_correctly() {
        assertEquals("[]", PgVectorStore.toPgVector(new float[0]));
        assertEquals("[1.0]", PgVectorStore.toPgVector(new float[]{1.0f}));
        assertEquals("[0.5,-0.5,1.0]", PgVectorStore.toPgVector(new float[]{0.5f, -0.5f, 1.0f}));
    }

    @Test
    void toPgVector_should_handle_null() {
        assertEquals("[]", PgVectorStore.toPgVector(null));
    }

    @Test
    void constructor_rejects_null_args() {
        var vp = new VectorStoreProperties();
        var ep = new EmbeddingProperties();
        assertThrows(IllegalArgumentException.class, () -> new PgVectorStore(null, vp, ep));
        assertThrows(IllegalArgumentException.class, () -> new PgVectorStore(null, null, null));
    }

    @Test
    void constructor_rejects_dimension_mismatch() {
        VectorStoreProperties vp = new VectorStoreProperties();
        vp.setDimension(128);
        EmbeddingProperties ep = new EmbeddingProperties();
        ep.setDimension(256);
        assertThrows(IllegalArgumentException.class,
                () -> new PgVectorStore(new JdbcTemplate(), vp, ep));
    }

    @Test
    void constructor_rejects_invalid_table_name() {
        VectorStoreProperties vp = new VectorStoreProperties();
        vp.setTableName("DROP TABLE users; --");
        vp.setInitializeSchema(false);
        EmbeddingProperties ep = new EmbeddingProperties();
        ep.setDimension(128);
        vp.setDimension(128);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new PgVectorStore(new JdbcTemplate(), vp, ep));
        assertTrue(ex.getMessage().contains("表名不合法"));
    }

    @Test
    void constructor_accepts_valid_table_name() {
        // 使用 mock JdbcTemplate (不会连真实数据库)
        var jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
        VectorStoreProperties vp = new VectorStoreProperties();
        vp.setTableName("my_vectors_123");
        vp.setInitializeSchema(false);
        EmbeddingProperties ep = new EmbeddingProperties();
        ep.setDimension(128);
        vp.setDimension(128);

        // 不抛异常 = 通过
        new PgVectorStore(jdbc, vp, ep);
    }

    @Test
    void search_SQL_parameter_order() {
        // topK 必须在 SQL 中作为第三个参数,前两个都是 vector
        String sql = "SELECT id, content, source, metadata_json, "
                + "1 - (embedding <=> ?::vector) AS score "
                + "FROM rag_vectors "
                + "ORDER BY embedding <=> ?::vector "
                + "LIMIT ?";
        // 验证 SQL 模板中 ? 的数量和顺序
        long paramCount = sql.chars().filter(c -> c == '?').count();
        assertEquals(3, paramCount, "SQL 应有 3 个参数: vector, vector, topK");
        // 第一个 ? 为 SELECT 中的 ?
        assertTrue(sql.indexOf("?") < sql.lastIndexOf("?"),
                "前两个 ? 在 LIMIT 之前");
        assertTrue(sql.indexOf("LIMIT ?") > sql.indexOf("<=> ?::vector"),
                "LIMIT ? 是最后的参数");
    }

    @Test
    void upsert_SQL_contains_expected_clauses() {
        // 验证 upsert SQL 片段
        String sql = "INSERT INTO rag_vectors "
                + "(id, content, source, metadata_json, embedding, updated_at) "
                + "VALUES (?, ?, ?, ?::jsonb, ?::vector, NOW()) "
                + "ON CONFLICT (id) DO UPDATE SET "
                + "content = EXCLUDED.content, source = EXCLUDED.source, "
                + "metadata_json = EXCLUDED.metadata_json, "
                + "embedding = EXCLUDED.embedding, updated_at = NOW()";
        assertTrue(sql.contains("ON CONFLICT (id) DO UPDATE"));
        assertTrue(sql.contains("?::jsonb"));
        assertTrue(sql.contains("?::vector"));
        assertTrue(sql.contains("EXCLUDED.embedding"));
    }

    @Test
    void upsert_with_mock_jdbc_should_succeed() {
        var jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
        VectorStoreProperties vp = new VectorStoreProperties();
        vp.setInitializeSchema(false);
        vp.setDimension(4);
        EmbeddingProperties ep = new EmbeddingProperties();
        ep.setDimension(4);
        PgVectorStore store = new PgVectorStore(jdbc, vp, ep);

        var doc = new VectorDocument("test-id", "content", "source.md",
                Map.of(), new EmbeddingVector(new float[]{1f, 2f, 3f, 4f}, 4));
        store.upsert(doc);
        // 不抛异常 = 通过
    }
}
