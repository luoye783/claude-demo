package com.example.claudedemo.agent.rag.store;

import com.example.claudedemo.agent.rag.embedding.EmbeddingProperties;
import com.example.claudedemo.agent.rag.embedding.EmbeddingVector;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * PostgreSQL + pgvector 向量存储(V2 第十一阶段 RAG V6).
 *
 * <p>基于 {@link JdbcTemplate} 与 pgvector 的 {@code <=>} cosine distance 运算符,
 * 提供持久化的向量 upsert 与相似度搜索。
 *
 * <p><b>表结构(V6)</b>:
 * <pre>{@code
 * CREATE TABLE rag_vectors (
 *   id VARCHAR(255) PRIMARY KEY,
 *   document_id VARCHAR(255),
 *   content TEXT NOT NULL,
 *   source VARCHAR(500),
 *   metadata_json JSONB DEFAULT '{}'::jsonb,
 *   chunk_hash VARCHAR(64),
 *   embedding vector(dimension) NOT NULL,
 *   updated_at TIMESTAMP DEFAULT NOW()
 * );
 * CREATE INDEX idx_rag_vectors_document_id ON rag_vectors (document_id);
 * CREATE INDEX idx_rag_vectors_embedding ON rag_vectors
 *   USING hnsw (embedding vector_cosine_ops);
 * }</pre>
 *
 * <p><b>V6 新增索引生命周期方法</b>:
 * {@link #clear} / {@link #deleteByDocumentId} / {@link #count} /
 * {@link #exists} / {@link #findById} / {@link #getDocumentIds}
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

    // ==================== 写入 ====================

    @Override
    public void upsert(VectorDocument document) {
        if (document == null || document.id() == null) return;
        String sql = "INSERT INTO " + tableName
                + " (id, document_id, content, source, metadata_json, chunk_hash, embedding, updated_at) "
                + "VALUES (?, ?, ?, ?, ?::jsonb, ?, ?::vector, NOW()) "
                + "ON CONFLICT (id) DO UPDATE SET "
                + "document_id = EXCLUDED.document_id, "
                + "content = EXCLUDED.content, source = EXCLUDED.source, "
                + "metadata_json = EXCLUDED.metadata_json, "
                + "chunk_hash = EXCLUDED.chunk_hash, "
                + "embedding = EXCLUDED.embedding, updated_at = NOW()";
        jdbc.update(sql,
                document.id(),
                document.documentId(),
                document.content(),
                document.source(),
                toJson(document.metadataView()),
                document.chunkHash(),
                toPgVector(document.embedding().values()));
    }

    // ==================== 检索 ====================

    @Override
    public List<VectorSearchResult> search(EmbeddingVector query, int topK) {
        if (query == null || topK <= 0) return List.of();
        String sql = "SELECT id, document_id, content, source, metadata_json, chunk_hash, "
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

    // ==================== 索引生命周期(V6 新增) ====================

    @Override
    public void clear() {
        jdbc.update("DELETE FROM " + tableName);
        log.info("PgVectorStore 已清空: table={}", tableName);
    }

    @Override
    public void deleteByDocumentId(String documentId) {
        if (documentId == null) return;
        int deleted = jdbc.update("DELETE FROM " + tableName + " WHERE document_id = ?", documentId);
        log.debug("deleteByDocumentId: docId={}, deleted={} rows", documentId, deleted);
    }

    @Override
    public long count() {
        Long cnt = jdbc.queryForObject("SELECT COUNT(*) FROM " + tableName, Long.class);
        return cnt == null ? 0 : cnt;
    }

    @Override
    public boolean exists(String id) {
        if (id == null) return false;
        Long cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + tableName + " WHERE id = ?", Long.class, id);
        return cnt != null && cnt > 0;
    }

    @Override
    public Optional<VectorDocument> findById(String id) {
        if (id == null) return Optional.empty();
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, document_id, content, source, metadata_json, chunk_hash "
                + "FROM " + tableName + " WHERE id = ?", id);
        if (rows.isEmpty()) return Optional.empty();
        return Optional.of(rowToDocument(rows.get(0)));
    }

    @Override
    public Set<String> getDocumentIds() {
        List<String> ids = jdbc.queryForList(
                "SELECT DISTINCT document_id FROM " + tableName
                + " WHERE document_id IS NOT NULL", String.class);
        return new HashSet<>(ids);
    }

    // ==================== 内部方法 ====================

    /**
     * 初始化数据库 schema: extension vector → 表 → 索引.
     */
    void initializeSchema() {
        jdbc.execute("CREATE EXTENSION IF NOT EXISTS vector");
        jdbc.execute("CREATE TABLE IF NOT EXISTS " + tableName + " ("
                + "id VARCHAR(255) PRIMARY KEY, "
                + "document_id VARCHAR(255), "
                + "content TEXT NOT NULL, "
                + "source VARCHAR(500), "
                + "metadata_json JSONB DEFAULT '{}'::jsonb, "
                + "chunk_hash VARCHAR(64), "
                + "embedding vector(" + dimension + ") NOT NULL, "
                + "updated_at TIMESTAMP DEFAULT NOW())");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_" + tableName + "_embedding "
                + "ON " + tableName + " USING hnsw (embedding vector_cosine_ops)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_" + tableName + "_document_id "
                + "ON " + tableName + " (document_id)");
        log.info("Schema 初始化完成: table={}, dimension={}, index=hnsw+document_id", tableName, dimension);
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
        VectorDocument doc = rowToDocument(row);
        double score = row.get("score") instanceof Number n ? n.doubleValue() : 0.0;
        return new VectorSearchResult(doc, score);
    }

    /**
     * 行数据 → VectorDocument(不含 embedding,仅供 findById / search 结果映射).
     */
    private VectorDocument rowToDocument(Map<String, Object> row) {
        String id = (String) row.get("id");
        String documentId = (String) row.get("document_id");
        String content = (String) row.get("content");
        String source = (String) row.get("source");
        String chunkHash = (String) row.get("chunk_hash");
        Map<String, Object> meta = parseMetadata(row.get("metadata_json"));
        return new VectorDocument(id, documentId, content, source, chunkHash, meta, null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseMetadata(Object metadataObj) {
        if (metadataObj instanceof String jsonStr && !jsonStr.isEmpty()) {
            try {
                return MAPPER.readValue(jsonStr, new TypeReference<Map<String, Object>>() {});
            } catch (Exception ignored) {}
        }
        return Map.of();
    }
}
