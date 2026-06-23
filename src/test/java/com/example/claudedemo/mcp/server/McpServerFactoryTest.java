package com.example.claudedemo.mcp.server;

import com.example.claudedemo.agent.SchemaSelector;
import com.example.claudedemo.sql.SchemaIntrospector;
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

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * {@link McpServerFactory} Step 2 单测.
 *
 * <p>验证:
 * <ul>
 *   <li>工厂接受 {@link SchemaSelector} 后能构造并启动 server</li>
 *   <li>返回的 server 已启动,工具列表包含 get_schema</li>
 *   <li>工具定义(name / description / inputSchema)正确</li>
 *   <li>工具处理器返回真实的 schema 文本</li>
 *   <li>server 能被 close</li>
 * </ul>
 *
 * <p>注意:transport 绑定的 {@code System.in} 在测试中处于"无数据"状态,
 * 内部读取线程会空转;本测试不与协议交互,仅验证构造路径与工具注册.
 *
 * @author claude-code
 * @since 0.0.1
 */
@JdbcTest
@Import({SchemaIntrospector.class, SchemaSelector.class})
class McpServerFactoryTest {

    @Autowired
    private SchemaSelector schemaSelector;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        // 创建测试表供 SchemaSelector 读取
        // 注意:@JdbcTest 的嵌入式 H2 在同一测试类内复用,需先 DROP 避免冲突
        jdbcTemplate.execute("DROP TABLE IF EXISTS test_users");
        jdbcTemplate.execute("CREATE TABLE test_users ("
                + "id INT NOT NULL,"
                + "name VARCHAR(50),"
                + "age INT"
                + ")");
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void start_withGetSchemaTool_registersOneTool() {
        McpServerFactory factory = new McpServerFactory(schemaSelector);

        McpSyncServer server = assertDoesNotThrow(factory::start,
                "factory.start() 不应抛异常");

        assertNotNull(server, "返回的 server 不应为 null");

        var tools = server.listTools();
        assertEquals(1, tools.size(), "Step 2 应注册 1 个工具");

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
        McpServerFactory factory = new McpServerFactory(schemaSelector);
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
        McpServerFactory factory = new McpServerFactory(null);
        McpServerFeatures.SyncToolSpecification spec = factory.buildGetSchemaTool();

        McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(
                "get_schema", Map.of());

        McpSchema.CallToolResult result = spec.callHandler().apply(exchange, request);

        assertTrue(result.isError(), "数据库未配置时应返回错误");
        String text = ((McpSchema.TextContent) result.content().getFirst()).text();
        assertTrue(text.contains("Error"), "错误信息应以 Error 开头");
    }
}
