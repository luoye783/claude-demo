package com.example.claudedemo.agent.rag.embedding;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link VolcengineEmbeddingClient} 手动集成测试.
 *
 * <p>只在以下条件同时满足时执行:
 * <ul>
 *   <li>{@code RUN_EMBEDDING_IT=true} (环境变量)</li>
 *   <li>{@code EMBEDDING_API_KEY=xxx} (环境变量,真实 API Key)</li>
 * </ul>
 *
 * <p>不默认执行,避免费用和网络依赖。
 *
 * @since 0.0.1
 */
@Tag("manual-it")
class VolcengineEmbeddingClientManualIT {

    private static final Logger log = LoggerFactory.getLogger(VolcengineEmbeddingClientManualIT.class);

    private static final String ENV_RUN = "RUN_EMBEDDING_IT";
    private static final String ENV_KEY = "EMBEDDING_API_KEY";

    private static VolcengineEmbeddingClient client;
    private static boolean shouldRun = false;
    private static int dimension;
    private static String modelName;

    @BeforeAll
    static void setUp() {
        String runFlag = System.getenv(ENV_RUN);
        String apiKey = System.getenv(ENV_KEY);

        if (!"true".equalsIgnoreCase(runFlag)) {
            log.info("跳过手动 IT: {} != true", ENV_RUN);
            shouldRun = false;
            return;
        }
        if (apiKey == null || apiKey.isBlank()) {
            log.info("跳过手动 IT: {} 未配置", ENV_KEY);
            shouldRun = false;
            return;
        }

        // 从实际配置读取(否则用默认值)
        dimension = 128; // doubao-embedding-vision 的维度
        modelName = "doubao-embedding-vision-251215";
        String baseUrl = "https://ark.cn-beijing.volces.com/api/v3/embeddings/multimodal";

        EmbeddingProperties props = new EmbeddingProperties();
        props.setProvider(EmbeddingProvider.VOLCENGINE);
        props.setModel(modelName);
        props.setBaseUrl(baseUrl);
        props.setApiKey(apiKey);
        props.setDimension(dimension);
        props.setTimeoutSeconds(30);
        props.setBatchSize(16);

        client = new VolcengineEmbeddingClient(props);
        shouldRun = true;
        log.info("手动 IT 已就绪:model={}, dimension={}, baseUrl={}", modelName, dimension, baseUrl);
    }

    @Test
    void it_should_embed_single_text() {
        if (!shouldRun) return;

        EmbeddingVector vec = client.embed("北京有多少有效用户?");
        assertNotNull(vec);
        assertEquals(dimension, vec.dimension(), "维度应为 " + dimension);
        assertTrue(vec.values().length > 0);
        log.info("embed 单条结果: dimension={}, values[0..5]={},{},{},{},{},{}",
                vec.dimension(),
                vec.values()[0], vec.values()[1], vec.values()[2],
                vec.values()[3], vec.values()[4], vec.values()[5]);
    }

    @Test
    void it_should_embed_multiple_texts() {
        if (!shouldRun) return;

        List<String> texts = List.of(
                "北京有多少有效用户?",
                "查询上海地区的用户数量",
                "users 表包含哪些字段"
        );
        List<EmbeddingVector> results = client.embedAll(texts);
        assertNotNull(results);
        assertEquals(texts.size(), results.size());
        for (int i = 0; i < results.size(); i++) {
            assertEquals(dimension, results.get(i).dimension(), "[" + i + "] 维度应为 " + dimension);
            assertFalse(results.get(i).values().length == 0, "[" + i + "] 向量不应为空");
        }
        log.info("embedAll {} 条: 全部维度={}, 首条 values[0..3]={},{},{},{}",
                results.size(), dimension,
                results.get(0).values()[0], results.get(0).values()[1],
                results.get(0).values()[2], results.get(0).values()[3]);
    }

    @Test
    void it_should_embed_with_batch() {
        if (!shouldRun) return;

        // batchSize=8, 共 10 条,触发 2 批次
        List<String> texts = List.of(
                "北京用户统计", "上海用户统计", "广州用户统计", "深圳用户统计",
                "杭州用户统计", "成都用户统计", "武汉用户统计", "南京用户统计",
                "重庆用户统计", "西安用户统计"
        );
        List<EmbeddingVector> results = client.embedAll(texts);
        assertNotNull(results);
        assertEquals(10, results.size());
        log.info("batch embed 10 条成功: 维度={}", results.get(0).dimension());
    }

    // 使用 org.junit.jupiter.api.Assertions.assertEquals
}
