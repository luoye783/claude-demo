package com.example.claudedemo.agent.rag.store;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 向量存储连接配置(V2 第十阶段 RAG V5).
 *
 * <p>对应 application.yml 中的 {@code vector-store} 配置块。
 * 与 {@link com.example.claudedemo.agent.rag.embedding.EmbeddingProperties}
 * 和 {@link com.example.claudedemo.agent.rag.RagProperties} 并列，独立运行。
 *
 * <p>默认 provider = IN_MEMORY,无需外部数据库。
 *
 * @since 0.0.1
 */
@ConfigurationProperties(prefix = "vector-store")
public class VectorStoreProperties {

    /** 存储提供者(in-memory / pgvector). */
    private VectorStoreProvider provider = VectorStoreProvider.IN_MEMORY;

    /** 数据库表名(仅 pgvector). */
    private String tableName = "rag_vectors";

    /** 向量维度,必须与 embedding.dimension 一致. */
    private int dimension = 128;

    /** 是否自动初始化数据库 schema(建表/索引). */
    private boolean initializeSchema = true;

    /** PostgreSQL 连接配置(仅 provider=pgvector). */
    private Pg pg = new Pg();

    public VectorStoreProvider getProvider() {
        return provider;
    }

    public void setProvider(VectorStoreProvider provider) {
        this.provider = provider;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public int getDimension() {
        return dimension;
    }

    public void setDimension(int dimension) {
        this.dimension = dimension;
    }

    public boolean isInitializeSchema() {
        return initializeSchema;
    }

    public void setInitializeSchema(boolean initializeSchema) {
        this.initializeSchema = initializeSchema;
    }

    public Pg getPg() {
        return pg;
    }

    public void setPg(Pg pg) {
        this.pg = pg;
    }

    /**
     * PostgreSQL 连接子配置.
     */
    public static class Pg {
        private String url = "jdbc:postgresql://localhost:5432/rag";
        private String username = "postgres";
        private String password = "";
        private String driverClassName = "org.postgresql.Driver";

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getDriverClassName() { return driverClassName; }
        public void setDriverClassName(String driverClassName) { this.driverClassName = driverClassName; }
    }
}
