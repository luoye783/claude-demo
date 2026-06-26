package com.example.claudedemo.agent.rag.embedding;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * {@link EmbeddingProperties} 单元测试.
 *
 * @since 0.0.1
 */
class EmbeddingPropertiesTest {

    @Test
    void should_have_default_values() {
        EmbeddingProperties p = new EmbeddingProperties();
        assertEquals(EmbeddingProvider.SIMPLE_HASH, p.getProvider());
        assertEquals("", p.getModel());
        assertEquals("", p.getBaseUrl());
        assertEquals("", p.getApiKey());
        assertEquals(128, p.getDimension());
        assertEquals(10, p.getTimeoutSeconds());
        assertEquals(16, p.getBatchSize());
    }

    @Test
    void should_set_and_get_provider() {
        EmbeddingProperties p = new EmbeddingProperties();
        p.setProvider(EmbeddingProvider.VOLCENGINE);
        assertEquals(EmbeddingProvider.VOLCENGINE, p.getProvider());
    }

    @Test
    void should_set_and_get_dimension() {
        EmbeddingProperties p = new EmbeddingProperties();
        p.setDimension(256);
        assertEquals(256, p.getDimension());
    }

    @Test
    void should_set_and_get_batchSize() {
        EmbeddingProperties p = new EmbeddingProperties();
        p.setBatchSize(32);
        assertEquals(32, p.getBatchSize());
    }

    @Test
    void should_set_and_get_timeout() {
        EmbeddingProperties p = new EmbeddingProperties();
        p.setTimeoutSeconds(30);
        assertEquals(30, p.getTimeoutSeconds());
    }

    @Test
    void should_set_and_get_model_url_key() {
        EmbeddingProperties p = new EmbeddingProperties();
        p.setModel("test-model");
        p.setBaseUrl("https://example.com/embed");
        p.setApiKey("sk-xxx");
        assertEquals("test-model", p.getModel());
        assertEquals("https://example.com/embed", p.getBaseUrl());
        assertEquals("sk-xxx", p.getApiKey());
    }

    @Test
    void should_have_non_null_default_provider() {
        assertNotNull(new EmbeddingProperties().getProvider());
    }
}
