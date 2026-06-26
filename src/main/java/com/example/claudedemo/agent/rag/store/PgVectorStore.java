package com.example.claudedemo.agent.rag.store;

import com.example.claudedemo.agent.rag.embedding.EmbeddingProperties;
import com.example.claudedemo.agent.rag.embedding.EmbeddingVector;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * PostgreSQL + pgvector 向量存储(V2 第十阶段 RAG V5).
 *
 * <p>基于 {@link JdbcTemplate} 与 pgvector 的 {@code <=>} cosine distance 运算符,
 * 提供持久化的向量 upsert 与相似度搜索。
 *
 * <p><b>表名白名单</b>:只允许 {@code [a-zA-Z_][a-zA-Z0-9_]*},防止 SQL 注入。
 *
 * <p><b>维度校验</b>:构造时检查 {@code VectorStoreProperties.dimension}
 * 必须等于 {@code EmbeddingProperties.dimension},不等则抛异常。
 *
 * @since 0.0.1
 */
public class PgVectorStore implements VectorStore {

    private static final Logger log = LoggerFactory.getLogger(PgVectorStore.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern TABLE_NAME_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

    private final JdbcTemplate jdbc;
    private final String tableName;
    private final int dimension;

    /**
     * @param jdbc      PostgreSQL 数据源的 JdbcTemplate
     * @param vecProps  向量存储配置(含 dimension / tableName / initializeSchema)
     * @param embProps  Embedding 配置(用于维度校验)
     */
    public PgVectorStore(JdbcTemplate jdbc, VectorStoreProperties vecProps,
                          EmbeddingProperties embProps) {
        if (jdbc == null) throw new IllegalArgumentException("JdbcTemplate must not be null");
        if (vecProps == null) throw new IllegalArgumentException("VectorStoreProperties must not be null");
        if (embProps == null) throw new IllegalArgumentException("EmbeddingProperties must not be null");

        // 维度一致性校验
        if (vecProps.getDimension() != embProps.getDimension()) {
            throw new IllegalArgumentException(
                    "vector-store.dimension (" + vecProps.getDimension()
                    + ") 与 embedding.dimension (" + embProps.getDimension() + ") 不一致");
        }

        // 表名白名单校验
        String tn = vecProps.getTableName();
        if (tn == null || !TABLE_NAME_PATTERN.matcher(tn).matches()) {
            throw new IllegalArgumentException(
                    "表名不合法: '" + tn + "', 必须匹配 ^[a-zA-Z_][a-zA-Z0-9_]*$");
        }

        this.jdbc = jdbc;
        this.tableName = tn;
        this.dimension = vecProps.getDimension();

        // 自动初始化 schema
        if (vecProps.isInitializeSchema()) {
            initializeSchema();
        }

        log.info("PgVectorStore 初始化完成: table={}, dimension={}", tableName, dimension);
    }

    @Override
    public void upsert(VectorDocument document) {
        if (document == null || document.id() == null) return;
        String sql = "INSERT INTO " + tableName
                + " (id, content, source, metadata_json, embedding, updated_at) "
                + "VALUES (?, ?, ?, ?::jsonb, ?::vector, NOW()) "
                + "ON CONFLICT (id) DO UPDATE SET "
                + "content = EXCLUDED.content, source = EXCLUDED.source, "
                + "metadata_json = EXCLUDED.metadata_json, "
                + "embedding = EXCLUDED.embedding, updated_at = NOW()";
        jdbc.update(sql,
                document.id(),
                document.content(),
                document.source(),
                toJson(document.metadataView()),
                toPgVector(document.embedding().values()));
    }

    @Override
    public List<VectorSearchResult> search(EmbeddingVector query, int topK) {
        if (query == null || topK <= 0) return List.of();
        String sql = "SELECT id, content, source, metadata_json, "
                + "1 - (embedding <=> ?::vector) AS score "
                + "FROM " + tableName + " "
                + "ORDER BY embedding <=> ?::vector "
                + "LIMIT ?";
        String vecLiteral = toPgVector(query.values());
        List<Map<String, Object>> rows = jdbc.queryForList(sql, vecLiteral, vecLiteral, topK);
        return rows.stream()
                .map(this::toSearchResult)
                .filter(r -> r.score() > 0.0)
                .toList();
    }

    // ==================== 内部方法 ====================

    /**
     * 初始化数据库 schema: extension vector → 表 → 索引.
     */
    private void initializeSchema() {
        jdbc.execute("CREATE EXTENSION IF NOT EXISTS vector");
        jdbc.execute("CREATE TABLE IF NOT EXISTS " + tableName + " ("
                + "id VARCHAR(255) PRIMARY KEY, "
                + "content TEXT NOT NULL, "
                + "source VARCHAR(500), "
                + "metadata_json JSONB DEFAULT '{}'::jsonb, "
                + "embedding vector(" + dimension + ") NOT NULL, "
                + "updated_at TIMESTAMP DEFAULT NOW())");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_" + tableName + "_embedding "
                + "ON " + tableName + " USING hnsw (embedding vector_cosine_ops)");
        log.info("Schema 初始化完成: table={}, dimension={}, index=hnsw", tableName, dimension);
    }

    /**
     * float[] → PostgreSQL vector 字面量,格式 {@code [0.1,0.2,0.3]}.
     */
    static String toPgVector(float[] values) {
        if (values == null || values.length == 0) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(values[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Map → JSON 字符串.
     */
    private String toJson(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) return "{}";
        try {
            return MAPPER.writeValueAsString(metadata);
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * 查询结果行 → VectorSearchResult.
     */
    private VectorSearchResult toSearchResult(Map<String, Object> row) {
        String id = (String) row.get("id");
        String content = (String) row.get("content");
        String source = (String) row.get("source");
        double score = row.get("score") instanceof Number n ? n.doubleValue() : 0.0;
        // metadata_json 以 String 或 PGobject 返回,简单处理
        Map<String, Object> meta = Map.of();
        Object metadataObj = row.get("metadata_json");
        if (metadataObj instanceof String jsonStr && !jsonStr.isEmpty()) {
            try {
                meta = MAPPER.readValue(jsonStr, Map.class);
            } catch (Exception ignored) {}
        }
        VectorDocument doc = new VectorDocument(id, content, source, meta, null);
        return new VectorSearchResult(doc, score);
    }
}
