package com.example.claudedemo.agent.rag.embedding;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link VolcengineEmbeddingClient} 单元测试.
 *
 * <p>使用 Mock {@link RestClient} 模拟 HTTP 调用,不依赖真实网络.
 *
 * @since 0.0.1
 */
class VolcengineEmbeddingClientTest {

    private EmbeddingProperties props;
    private RestClient restClient;

    @BeforeEach
    void setUp() {
        props = new EmbeddingProperties();
        props.setProvider(EmbeddingProvider.VOLCENGINE);
        props.setModel("test-model");
        props.setBaseUrl("https://api.example.com/embed");
        props.setApiKey("sk-test-key");
        props.setDimension(4);
        props.setBatchSize(2);

        restClient = mock(RestClient.class);
    }

    /** 构造一个返回固定 json 的 mock restClient 调用链. */
    private void mockResponse(String json) {
        RestClient.RequestBodyUriSpec uriSpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec reqSpec = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec respSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.post()).thenReturn(uriSpec);
        when(uriSpec.body(anyString())).thenReturn(reqSpec);
        when(reqSpec.retrieve()).thenReturn(respSpec);
        when(respSpec.body(String.class)).thenReturn(json);
    }

    @Test
    void should_embed_single_text() {
        String json = "{\"data\":[{\"index\":0,\"embedding\":[0.1,0.2,0.3,0.4]}]}";
        mockResponse(json);

        VolcengineEmbeddingClient client = new VolcengineEmbeddingClient(props, restClient);
        EmbeddingVector vec = client.embed("hello");
        assertEquals(4, vec.dimension());
        assertEquals(0.1f, vec.values()[0]);
        assertEquals(0.4f, vec.values()[3]);
    }

    @Test
    void should_embed_all_multiple_texts() {
        String json = "{\"data\":[" +
                "{\"index\":0,\"embedding\":[0.1,0.2,0.3,0.4]}," +
                "{\"index\":1,\"embedding\":[0.5,0.6,0.7,0.8]}" +
                "]}";
        mockResponse(json);

        VolcengineEmbeddingClient client = new VolcengineEmbeddingClient(props, restClient);
        List<EmbeddingVector> vecs = client.embedAll(List.of("a", "b"));
        assertEquals(2, vecs.size());
        assertEquals(0.1f, vecs.get(0).values()[0]);
        assertEquals(0.5f, vecs.get(1).values()[0]);
    }

    @Test
    void should_split_batches_when_exceeding_batchSize() {
        // batchSize=2, 3 texts → 2 批次
        // 第一次调用返回前 2 个,第二次返回第 3 个
        RestClient.RequestBodyUriSpec uriSpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec reqSpec = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec respSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.post()).thenReturn(uriSpec);
        when(uriSpec.body(anyString())).thenReturn(reqSpec);
        when(reqSpec.retrieve()).thenReturn(respSpec);
        // 第 1 批 (start=0): 2 items
        // 第 2 批 (start=2): 1 item with global index 2
        String batch1 = "{\"data\":[" +
                "{\"index\":0,\"embedding\":[1.0,0.0,0.0,0.0]}," +
                "{\"index\":1,\"embedding\":[0.0,1.0,0.0,0.0]}]}";
        String batch2 = "{\"data\":[" +
                "{\"index\":0,\"embedding\":[0.0,0.0,1.0,0.0]}]}";
        when(respSpec.body(String.class))
                .thenReturn(batch1)
                .thenReturn(batch2);

        VolcengineEmbeddingClient client = new VolcengineEmbeddingClient(props, restClient);
        List<EmbeddingVector> vecs = client.embedAll(List.of("a", "b", "c"));
        assertEquals(3, vecs.size());
        // 全局 index: 0→a, 1→b, 2→c → 顺序正确
        assertEquals(1.0f, vecs.get(0).values()[0]);
        assertEquals(1.0f, vecs.get(1).values()[1]);
        assertEquals(1.0f, vecs.get(2).values()[2]);
    }

    @Test
    void should_throw_when_dimension_mismatch() {
        // dimension=4 但返回 3 维
        String json = "{\"data\":[{\"index\":0,\"embedding\":[0.1,0.2,0.3]}]}";
        mockResponse(json);

        VolcengineEmbeddingClient client = new VolcengineEmbeddingClient(props, restClient);
        assertThrows(EmbeddingClientException.class, () -> client.embed("x"));
    }

    @Test
    void should_throw_when_data_empty() {
        String json = "{\"data\":[]}";
        mockResponse(json);

        VolcengineEmbeddingClient client = new VolcengineEmbeddingClient(props, restClient);
        assertThrows(EmbeddingClientException.class, () -> client.embed("x"));
    }

    @Test
    void should_throw_when_data_missing() {
        String json = "{}";
        mockResponse(json);

        VolcengineEmbeddingClient client = new VolcengineEmbeddingClient(props, restClient);
        assertThrows(EmbeddingClientException.class, () -> client.embed("x"));
    }

    @Test
    void should_throw_when_embedding_missing() {
        String json = "{\"data\":[{\"index\":0}]}";
        mockResponse(json);

        VolcengineEmbeddingClient client = new VolcengineEmbeddingClient(props, restClient);
        assertThrows(EmbeddingClientException.class, () -> client.embed("x"));
    }

    @Test
    void should_throw_when_response_is_null() {
        mockResponse(null);

        VolcengineEmbeddingClient client = new VolcengineEmbeddingClient(props, restClient);
        EmbeddingClientException ex = assertThrows(EmbeddingClientException.class, () -> client.embed("x"));
        assertTrue(ex.getMessage().contains("空响应"));
    }

    @Test
    void should_return_empty_for_null_or_empty_input() {
        VolcengineEmbeddingClient client = new VolcengineEmbeddingClient(props, restClient);
        assertTrue(client.embedAll(null).isEmpty());
        assertTrue(client.embedAll(List.of()).isEmpty());
    }

    @Test
    void should_reject_invalid_config() {
        EmbeddingProperties empty = new EmbeddingProperties();
        assertThrows(IllegalArgumentException.class, () -> new VolcengineEmbeddingClient(null));
        assertThrows(IllegalArgumentException.class, () -> new VolcengineEmbeddingClient(empty, mock(RestClient.class)));
    }

    @Test
    void should_parse_json_response_correctly() {
        String json = "{\"data\":[{\"index\":0,\"embedding\":[-0.5,0.0,0.5,1.0]}]}";
        mockResponse(json);

        VolcengineEmbeddingClient client = new VolcengineEmbeddingClient(props, restClient);
        EmbeddingVector vec = client.embed("test");
        assertEquals(4, vec.dimension());
        assertEquals(-0.5f, vec.values()[0]);
        assertEquals(1.0f, vec.values()[3]);
        assertNotNull(vec);
    }

    @Test
    void should_maintain_order_across_batches() {
        // batchSize=2, 4 texts → 2 batches × 2
        RestClient.RequestBodyUriSpec uriSpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec reqSpec = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec respSpec = mock(RestClient.ResponseSpec.class);
        when(restClient.post()).thenReturn(uriSpec);
        when(uriSpec.body(anyString())).thenReturn(reqSpec);
        when(reqSpec.retrieve()).thenReturn(respSpec);
        // 故意乱序 index: batch2 index 在前
        String batch1 = "{\"data\":[{\"index\":0,\"embedding\":[1,0,0,0]},{\"index\":1,\"embedding\":[0,1,0,0]}]}";
        String batch2 = "{\"data\":[{\"index\":0,\"embedding\":[0,0,1,0]},{\"index\":1,\"embedding\":[0,0,0,1]}]}";
        when(respSpec.body(String.class)).thenReturn(batch1).thenReturn(batch2);

        VolcengineEmbeddingClient client = new VolcengineEmbeddingClient(props, restClient);
        List<EmbeddingVector> vecs = client.embedAll(List.of("a", "b", "c", "d"));
        assertEquals(4, vecs.size());
        assertEquals(1.0f, vecs.get(0).values()[0]); // a
        assertEquals(0.0f, vecs.get(0).values()[1]);
        assertEquals(1.0f, vecs.get(1).values()[1]); // b
        assertEquals(1.0f, vecs.get(2).values()[2]); // c
        assertEquals(1.0f, vecs.get(3).values()[3]); // d
    }
}
