package com.example.claudedemo.mcp.server;

import com.example.claudedemo.agent.SchemaSelector;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * MCP Server 工厂:V1 构建空 server;Step 2 注册 get_schema tool.
 *
 * <p><b>职责</b>:
 * <ul>
 *   <li>构造 {@link McpJsonMapper}(复用 Spring Boot 自带的 Jackson 2,见 {@link Jackson2McpJsonMapper})</li>
 *   <li>构造 {@link StdioServerTransportProvider}(绑定 {@code System.in} / {@code System.out})</li>
 *   <li>用 {@link McpServer#sync} 构造 {@link McpSyncServer}</li>
 *   <li>注册 get_schema tool(Step 2),后续 Step 3 加入 execute_sql</li>
 * </ul>
 *
 * <p><b>Step 2 状态</b>:注册 get_schema tool,复用 {@link SchemaSelector}.
 * 当 schemaSelector 为 null(未配置数据源)时,工具调用返回错误信息.
 *
 * <p><b>不变量</b>:
 * <ul>
 *   <li>stdout 只能用于 MCP JSON-RPC 帧;所有日志必须打 stderr(由 {@code logback-mcp.xml} 强制)</li>
 *   <li>{@link #start()} 幂等:同一工厂多次调用会产生多个 server(预期外),仅调用一次</li>
 * </ul>
 *
 * @author claude-code
 * @since 0.0.1
 */
@Component
public class McpServerFactory {

    private static final Logger log = LoggerFactory.getLogger(McpServerFactory.class);

    /** 为空时表数据库未配置(大部分测试场景无需数据库). */
    private final SchemaSelector schemaSelector;

    @Autowired(required = false)
    public McpServerFactory(SchemaSelector schemaSelector) {
        this.schemaSelector = schemaSelector;
    }

    /**
     * 构造 get_schema 的 MCP Tool 规格.
     *
     * <p>包级可见,允许测试直接验证工具定义与处理器.
     *
     * @return get_schema 工具规格(包含定义与调用处理器)
     */
    McpServerFeatures.SyncToolSpecification buildGetSchemaTool() {
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name("get_schema")
                .description("获取数据库中所有表的结构(表名 + 字段名 + 字段类型 + 是否非空 + 字段注释)。"
                        + "必须作为首个工具调用,以便了解有哪些表可用。")
                .inputSchema(new McpSchema.JsonSchema(
                        "object", Map.of(), List.of(), false, null, null))
                .build();

        return new McpServerFeatures.SyncToolSpecification(
                tool,
                (exchange, request) -> {
                    if (schemaSelector == null) {
                        return new McpSchema.CallToolResult(
                                List.of(new McpSchema.TextContent(
                                        "Error: database not configured")),
                                true, null, null);
                    }
                    String schemaText = schemaSelector.select("");
                    return new McpSchema.CallToolResult(
                            List.of(new McpSchema.TextContent(schemaText)),
                            false, null, null);
                }
        );
    }

    /**
     * 构造并启动 MCP server.
     *
     * <p>server 启动后会在后台线程读取 stdin,持续到 EOF 或被 close.
     * 主线程需自行阻塞(参见 {@code McpServerMain}).
     *
     * @return 已启动的 {@link McpSyncServer},调用方负责在适当时机 close
     */
    public McpSyncServer start() {
        // 1. 构造 JSON mapper(复用 Spring Boot 自带的 Jackson 2 ObjectMapper)
        McpJsonMapper jsonMapper = new Jackson2McpJsonMapper();
        log.info("Loaded McpJsonMapper: {}", jsonMapper.getClass().getName());

        // 2. 构造 STDIO transport
        StdioServerTransportProvider transport = new StdioServerTransportProvider(jsonMapper);
        log.info("StdioServerTransportProvider initialized (stdin/stdout bound)");

        // 3. 构造 server:注册 get_schema tool
        McpSchema.ServerCapabilities capabilities = McpSchema.ServerCapabilities.builder()
                .tools(true)
                .build();

        McpServerFeatures.SyncToolSpecification getSchemaSpec = buildGetSchemaTool();
        McpSyncServer server = McpServer.sync(transport)
                .serverInfo(McpServerMetadata.SERVER_NAME, McpServerMetadata.SERVER_VERSION)
                .capabilities(capabilities)
                .tools(getSchemaSpec)
                .build();

        log.info("MCP server started: name={}, version={}, toolCount={}",
                McpServerMetadata.SERVER_NAME,
                McpServerMetadata.SERVER_VERSION,
                server.listTools().size());
        log.info("MCP server tools: {}", server.listTools().stream()
                .map(McpSchema.Tool::name).toList());
        log.info("MCP server is now listening on stdin. Send JSON-RPC frames to interact.");

        return server;
    }
}
