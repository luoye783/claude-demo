package com.example.claudedemo.mcp.server;

import com.example.claudedemo.agent.SchemaSelector;
import com.example.claudedemo.sql.SqlErrorCode;
import com.example.claudedemo.sql.SqlExecutor;
import com.example.claudedemo.sql.SqlValidationException;
import com.example.claudedemo.sql.SqlValidator;
import com.example.claudedemo.sql.ValidatedSql;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * MCP Server 工厂:Step 2 注册 get_schema, Step 3 注册 execute_sql.
 *
 * <p><b>职责</b>:
 * <ul>
 *   <li>构造 {@link McpJsonMapper}(复用 Spring Boot 自带的 Jackson 2,见 {@link Jackson2McpJsonMapper})</li>
 *   <li>构造 {@link StdioServerTransportProvider}(绑定 {@code System.in} / {@code System.out})</li>
 *   <li>用 {@link McpServer#sync} 构造 {@link McpSyncServer}</li>
 *   <li>注册 get_schema tool(Step 2) 与 execute_sql tool(Step 3)</li>
 * </ul>
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

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private final SchemaSelector schemaSelector;
    private final SqlValidator sqlValidator;
    private final SqlExecutor sqlExecutor;

    @Autowired
    public McpServerFactory(
            @Autowired(required = false) SchemaSelector schemaSelector,
            @Autowired(required = false) SqlValidator sqlValidator,
            @Autowired(required = false) SqlExecutor sqlExecutor) {
        this.schemaSelector = schemaSelector;
        this.sqlValidator = sqlValidator;
        this.sqlExecutor = sqlExecutor;
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
     * 构造 execute_sql 的 MCP Tool 规格.
     *
     * <p>入参:
     * <ul>
     *   <li>{@code sql} — SQL 查询语句(仅 SELECT,必填)</li>
     * </ul>
     *
     * <p>返回 JSON:
     * <ul>
     *   <li>{@code sql} — 实际执行的 SQL</li>
     *   <li>{@code rowCount} — 返回行数</li>
     *   <li>{@code rows} — 数据行列表</li>
     * </ul>
     *
     * @return execute_sql 工具规格
     */
    McpServerFeatures.SyncToolSpecification buildExecuteSqlTool() {
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name("execute_sql")
                .description("执行 SQL 查询（仅支持 SELECT 语句），返回 JSON 格式的结果。"
                        + "DELETE / UPDATE / INSERT 等 DML 操作将被拒绝。")
                .inputSchema(new McpSchema.JsonSchema(
                        "object",
                        Map.of("sql", Map.of(
                                "type", "string",
                                "description", "SQL 查询语句（仅 SELECT）")),
                        List.of("sql"),
                        false, null, null))
                .build();

        return new McpServerFeatures.SyncToolSpecification(
                tool,
                (exchange, request) -> {
                    // 1. 校验 sql 参数
                    Object sqlObj = request.arguments().get("sql");
                    if (sqlObj == null || !(sqlObj instanceof String sql) || sql.trim().isEmpty()) {
                        return errorResult("缺少必填参数 sql");
                    }

                    // 2. 校验 SQL（SqlValidator 拒绝非 SELECT）
                    ValidatedSql validated;
                    try {
                        validated = sqlValidator.validate(sql);
                    } catch (SqlValidationException e) {
                        return errorResult(e.getMessage());
                    }

                    // 3. 执行 SQL
                    List<Map<String, Object>> rows;
                    try {
                        rows = sqlExecutor.execute(validated);
                    } catch (Exception e) {
                        log.warn("SQL 执行失败: sql={}, error={}", validated.sql(), e.toString());
                        return errorResult("SQL 执行失败: " + e.getMessage());
                    }

                    // 4. 格式化为 JSON 返回
                    try {
                        String json = JSON_MAPPER.writeValueAsString(Map.of(
                                "sql", validated.sql(),
                                "rowCount", rows.size(),
                                "rows", rows
                        ));
                        return new McpSchema.CallToolResult(
                                List.of(new McpSchema.TextContent(json)),
                                false, null, null);
                    } catch (Exception e) {
                        log.warn("JSON 序列化失败", e);
                        return errorResult("结果格式化失败");
                    }
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

        // 3. 构造 server:注册 get_schema + execute_sql tool
        McpSchema.ServerCapabilities capabilities = McpSchema.ServerCapabilities.builder()
                .tools(true)
                .build();

        McpServerFeatures.SyncToolSpecification getSchemaSpec = buildGetSchemaTool();
        McpServerFeatures.SyncToolSpecification executeSqlSpec = buildExecuteSqlTool();
        McpSyncServer server = McpServer.sync(transport)
                .serverInfo(McpServerMetadata.SERVER_NAME, McpServerMetadata.SERVER_VERSION)
                .capabilities(capabilities)
                .tools(getSchemaSpec, executeSqlSpec)
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

    /**
     * 构造错误返回结果.
     */
    private static McpSchema.CallToolResult errorResult(String message) {
        return new McpSchema.CallToolResult(
                List.of(new McpSchema.TextContent("Error: " + message)),
                true, null, null);
    }
}
