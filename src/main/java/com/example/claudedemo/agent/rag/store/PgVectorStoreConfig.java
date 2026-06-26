package com.example.claudedemo.agent.rag.store;

import com.example.claudedemo.agent.rag.embedding.EmbeddingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * PgVectorStore 独立装配配置(V2 第十阶段 RAG V5).
 *
 * <p>仅在 {@code vector-store.provider=pgvector} 时生效。
 * 创建独立的 PostgreSQL DataSource + JdbcTemplate + PgVectorStore,
 * 不干扰主上下文的其他数据库连接。
 *
 * @since 0.0.1
 */
@Configuration
@Conditional(PgVectorStoreCondition.class)
@EnableConfigurationProperties(VectorStoreProperties.class)
public class PgVectorStoreConfig {

    private static final Logger log = LoggerFactory.getLogger(PgVectorStoreConfig.class);

    /**
     * 专用 PostgreSQL 数据源(仅 pgvector 使用).
     */
    @Bean
    public DataSource pgVectorDataSource(VectorStoreProperties props) {
        VectorStoreProperties.Pg pg = props.getPg();
        return org.springframework.boot.jdbc.DataSourceBuilder.create()
                .type(com.zaxxer.hikari.HikariDataSource.class)
                .driverClassName(pg.getDriverClassName())
                .url(pg.getUrl())
                .username(pg.getUsername())
                .password(pg.getPassword())
                .build();
    }

    /**
     * 专用 JdbcTemplate(绑定 pgVectorDataSource).
     */
    @Bean
    public JdbcTemplate pgVectorJdbcTemplate(DataSource pgVectorDataSource) {
        return new JdbcTemplate(pgVectorDataSource);
    }

    /**
     * PgVectorStore 实例.
     */
    @Bean
    public VectorStore pgVectorStore(JdbcTemplate pgVectorJdbcTemplate,
                                      VectorStoreProperties vecProps,
                                      EmbeddingProperties embProps) {
        return new PgVectorStore(pgVectorJdbcTemplate, vecProps, embProps);
    }
}
