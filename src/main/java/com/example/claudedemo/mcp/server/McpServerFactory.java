package com.example.claudedemo.mcp.server;

import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * MCP Server 工厂:V1 仅构建空 server,后续 Step 注册 tool.
 *
 * <p><b>职责</b>:
 * <ul>
 *   <li>构造 {@link McpJsonMapper}(复用 Spring Boot 自带的 Jackson 2,见 {@link Jackson2McpJsonMapper})</li>
 *   <li>构造 {@link StdioServerTransportProvider}(绑定 {@code System.in} / {@code System.out})</li>
 *   <li>用 {@link McpServer#sync} 构造 {@link McpSyncServer}</li>
 *   <li>声明 tools 能力(true)但 V1 工具列表为空</li>
 * </ul>
 *
 * <p><b>Step 1 状态</b>:tool 列表为空,仅用于验证握手.真正的 tool 在 Step 2/3 加入.
 *
 * <p><b>不变量</b>:
 * <ul>
 *   <li>stdout 只能用于 MCP JSON-RPC 帧;所有日志必须打 stderr(由 {@code logback-mcp.xml} 强制)</li>
 *   <li>{@link #start()} 幂等:同一工厂多次调用会产生多个 server(预期外),V1 仅调用一次</li>
 * </ul>
 *
 * @author claude-code
 * @since 0.0.1
 */
@Component
public class McpServerFactory {

    private static final Logger log = LoggerFactory.getLogger(McpServerFactory.class);

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

        // 3. 构造 server:V1 工具列表为空
        McpSchema.ServerCapabilities capabilities = McpSchema.ServerCapabilities.builder()
                .tools(true)
                .build();

        McpSyncServer server = McpServer.sync(transport)
                .serverInfo(McpServerMetadata.SERVER_NAME, McpServerMetadata.SERVER_VERSION)
                .capabilities(capabilities)
                .tools(List.of())
                .build();

        log.info("MCP server started: name={}, version={}, toolCount={}",
                McpServerMetadata.SERVER_NAME,
                McpServerMetadata.SERVER_VERSION,
                server.listTools().size());
        log.info("MCP server is now listening on stdin. Send JSON-RPC frames to interact.");

        return server;
    }
}
