package com.example.claudedemo.agent.rag.store;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link VectorStoreProperties} 单元测试.
 *
 * @since 0.0.1
 */
class VectorStorePropertiesTest {

    @Test
    void should_have_default_values() {
        VectorStoreProperties p = new VectorStoreProperties();
        assertEquals(VectorStoreProvider.IN_MEMORY, p.getProvider());
        assertEquals("rag_vectors", p.getTableName());
        assertEquals(128, p.getDimension());
        assertTrue(p.isInitializeSchema());
    }

    @Test
    void should_set_and_get_provider() {
        VectorStoreProperties p = new VectorStoreProperties();
        p.setProvider(VectorStoreProvider.PGVECTOR);
        assertEquals(VectorStoreProvider.PGVECTOR, p.getProvider());
    }

    @Test
    void should_set_and_get_table_name() {
        VectorStoreProperties p = new VectorStoreProperties();
        p.setTableName("my_vectors");
        assertEquals("my_vectors", p.getTableName());
    }

    @Test
    void should_have_pg_sub_config() {
        VectorStoreProperties p = new VectorStoreProperties();
        VectorStoreProperties.Pg pg = p.getPg();
        assertNotNull(pg);
        assertEquals("jdbc:postgresql://localhost:5432/rag", pg.getUrl());
        assertEquals("postgres", pg.getUsername());
        assertEquals("org.postgresql.Driver", pg.getDriverClassName());
    }

    @Test
    void should_set_pg_values() {
        VectorStoreProperties p = new VectorStoreProperties();
        VectorStoreProperties.Pg pg = p.getPg();
        pg.setUrl("jdbc:postgresql://pg-host:5432/mydb");
        pg.setUsername("admin");
        pg.setPassword("secret");
        pg.setDriverClassName("org.postgresql.Driver");
        assertEquals("jdbc:postgresql://pg-host:5432/mydb", pg.getUrl());
        assertEquals("admin", pg.getUsername());
        assertEquals("secret", pg.getPassword());
    }

    @Test
    void should_support_initialize_schema_false() {
        VectorStoreProperties p = new VectorStoreProperties();
        p.setInitializeSchema(false);
        assertFalse(p.isInitializeSchema());
    }
}
