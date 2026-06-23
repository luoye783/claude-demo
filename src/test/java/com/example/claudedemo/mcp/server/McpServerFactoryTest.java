package com.example.claudedemo.mcp.server;

import com.example.claudedemo.agent.SchemaSelector;
import com.example.claudedemo.sql.SchemaIntrospector;
import com.example.claudedemo.sql.SqlExecutor;
import com.example.claudedemo.sql.SqlValidator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * {@link McpServerFactory} Step 2 + Step 3 单测.
 *
 * <p>验证:
 * <ul>
 *   <li>工厂能构造并启动 server,工具列表包含 get_schema 与 execute_sql</li>
 *   <li>get_schema 工具定义与处理器正确</li>
 *   <li>execute_sql: SELECT 成功返回 JSON</li>
 *   <li>execute_sql: DELETE 被 SqlValidator 拒绝</li>
 *   <li>execute_sql: sql 参数缺失返回错误</li>
 * </ul>
 *
 * @author claude-code
 * @since 0.0.1
 */
@JdbcTest
@Import({SchemaIntrospector.class, SchemaSelector.class, SqlValidator.class, SqlExecutor.class})
class McpServerFactoryTest {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    @Autowired
    private SchemaSelector schemaSelector;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    private SqlValidator sqlValidator;
    private SqlExecutor sqlExecutor;

    @BeforeEach
    void setUp() {
        sqlValidator = new SqlValidator();
        sqlExecutor = new SqlExecutor(dataSource);

        // Step 2 测试表
        jdbcTemplate.execute("DROP TABLE IF EXISTS test_users");
        jdbcTemplate.execute("CREATE TABLE test_users ("
                + "id INT NOT NULL,"
                + "name VARCHAR(50),"
                + "age INT"
                + ")");

        // Step 3 测试表
        jdbcTemplate.execute("DROP TABLE IF EXISTS test_data");
        jdbcTemplate.execute("CREATE TABLE test_data ("
                + "id INT NOT NULL,"
                + "val VARCHAR(50)"
                + ")");
        jdbcTemplate.execute("INSERT INTO test_data (id, val) VALUES (1, 'hello')");
        jdbcTemplate.execute("INSERT INTO test_data (id, val) VALUES (2, 'world')");
    }

    // ========== Step 2: get_schema tests ==========

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void start_withGetSchemaTool_registersOneTool() {
        McpServerFactory factory = new McpServerFactory(schemaSelector, sqlValidator, sqlExecutor);

        McpSyncServer server = assertDoesNotThrow(factory::start,
                "factory.start() 不应抛异常");

        assertNotNull(server, "返回的 server 不应为 null");

        var tools = server.listTools();
        assertEquals(2, tools.size(), "Step 3 应注册 2 个工具");

        McpSchema.Tool tool = tools.getFirst();
        assertEquals("get_schema", tool.name(), "工具名应为 get_schema");
        assertNotNull(tool.description(), "工具描述不应为 null");
        assertFalse(tool.description().isBlank(), "工具描述不应为空");
        assertNotNull(tool.inputSchema(), "工具 inputSchema 不应为 null");

        // 清理
        assertDoesNotThrow(server::close, "server.close() 不应抛异常");
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void getSchemaTool_handler_returnsSchemaContent() {
        McpServerFactory factory = new McpServerFactory(schemaSelector, sqlValidator, sqlExecutor);
        McpServerFeatures.SyncToolSpecification spec = factory.buildGetSchemaTool();

        assertEquals("get_schema", spec.tool().name());

        // 构造请求与 mock exchange,调用处理器
        McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(
                "get_schema", Map.of());

        McpSchema.CallToolResult result = spec.callHandler().apply(exchange, request);

        assertFalse(result.isError(), "get_schema 不应返回错误");
        assertNotNull(result.content(), "返回内容不应为 null");
        assertEquals(1, result.content().size(), "应返回 1 条 content");

        String text = ((McpSchema.TextContent) result.content().getFirst()).text();
        assertTrue(text.contains("TEST_USERS") || text.contains("test_users"),
                "schema 文本应包含测试表名,实际内容: " + text);
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void getSchemaTool_handler_withoutDatabase_returnsError() {
        // 传入 null SchemaSelector,模拟数据库未配置场景
        McpServerFactory factory = new McpServerFactory(null, null, null);
        McpServerFeatures.SyncToolSpecification spec = factory.buildGetSchemaTool();

        McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(
                "get_schema", Map.of());

        McpSchema.CallToolResult result = spec.callHandler().apply(exchange, request);

        assertTrue(result.isError(), "数据库未配置时应返回错误");
        String text = ((McpSchema.TextContent) result.content().getFirst()).text();
        assertTrue(text.contains("Error"), "错误信息应以 Error 开头");
    }

    // ========== Step 3: execute_sql tests ==========

    @Test
    void executeSqlTool_definition_isCorrect() {
        McpServerFactory factory = new McpServerFactory(null, sqlValidator, sqlExecutor);
        McpServerFeatures.SyncToolSpecification spec = factory.buildExecuteSqlTool();

        assertEquals("execute_sql", spec.tool().name());
        assertNotNull(spec.tool().description());
        assertFalse(spec.tool().description().isBlank());
        assertNotNull(spec.tool().inputSchema());
    }

    @Test
    void executeSqlTool_select_returnsJson() throws Exception {
        McpServerFactory factory = new McpServerFactory(null, sqlValidator, sqlExecutor);
        McpServerFeatures.SyncToolSpecification spec = factory.buildExecuteSqlTool();

        McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(
                "execute_sql", Map.of("sql", "SELECT * FROM test_data ORDER BY id"));

        McpSchema.CallToolResult result = spec.callHandler().apply(exchange, request);

        assertFalse(result.isError(), "SELECT 不应返回错误");
        String text = ((McpSchema.TextContent) result.content().getFirst()).text();

        // 解析 JSON 验证
        Map<String, Object> parsed = JSON_MAPPER.readValue(text,
                new TypeReference<Map<String, Object>>() {});
        assertEquals("SELECT * FROM test_data ORDER BY id LIMIT 100",
                parsed.get("sql"), "SQL 应包含自动注入的 LIMIT");
        assertEquals(2, parsed.get("rowCount"), "应有 2 行数据");

        // 验证 rows 内容
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) parsed.get("rows");
        assertEquals(2, rows.size());
        assertEquals(1, rows.get(0).get("ID"));
        assertEquals("hello", rows.get(0).get("VAL"));
        assertEquals(2, rows.get(1).get("ID"));
        assertEquals("world", rows.get(1).get("VAL"));
    }

    @Test
    void executeSqlTool_delete_isRejected() {
        McpServerFactory factory = new McpServerFactory(null, sqlValidator, sqlExecutor);
        McpServerFeatures.SyncToolSpecification spec = factory.buildExecuteSqlTool();

        McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(
                "execute_sql", Map.of("sql", "DELETE FROM test_data WHERE id = 1"));

        McpSchema.CallToolResult result = spec.callHandler().apply(exchange, request);

        assertTrue(result.isError(), "DELETE 应返回错误");
        String text = ((McpSchema.TextContent) result.content().getFirst()).text();
        assertTrue(text.contains("DML"), "错误信息应包含 DML 提示");
    }

    @Test
    void executeSqlTool_missingParam_returnsError() {
        McpServerFactory factory = new McpServerFactory(null, sqlValidator, sqlExecutor);
        McpServerFeatures.SyncToolSpecification spec = factory.buildExecuteSqlTool();

        McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(
                "execute_sql", Map.of()); // 无 sql 参数

        McpSchema.CallToolResult result = spec.callHandler().apply(exchange, request);

        assertTrue(result.isError(), "参数缺失应返回错误");
        String text = ((McpSchema.TextContent) result.content().getFirst()).text();
        assertTrue(text.contains("缺少必填参数"), "错误信息应提示缺失参数");
    }
}
