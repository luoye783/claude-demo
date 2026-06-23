package com.example.claudedemo.agent.mcp;

import com.example.claudedemo.mcp.server.Jackson2McpJsonMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * 基于 MCP SDK {@link StdioClientTransport} 的 {@link McpToolClient} 实现.
 *
 * <p>启动一个 MCP Server 子进程(命令与 {@code .mcp.json} 一致),
 * 通过 STDIO 传输层调用其 {@code get_schema} / {@code execute_sql} 工具.
 *
 * <p><b>生命周期</b>:
 * <ol>
 *   <li>构造器:启动子进程 + 建立 MCP 连接 + 握手</li>
 *   <li>{@link #getSchema()} / {@link #executeSql(String)}: 调用 MCP 工具</li>
 *   <li>{@link #close()}: 关闭连接 + 停止子进程</li>
 * </ol>
 *
 * <p><b>错误处理</b>:
 * <ul>
 *   <li>所有 MCP 调用异常被捕获,以 {@code "Error: [McpClientException] ..."} 返回</li>
 *   <li>不抛出 {@link RuntimeException} 给上层</li>
 * </ul>
 *
 * @author claude-code
 * @since 0.0.1
 */
public class StdioMcpToolClient implements McpToolClient {

    private static final Logger log = LoggerFactory.getLogger(StdioMcpToolClient.class);

    private final McpSyncClient client;

    /**
     * 构造 StdioMcpToolClient 并连接 MCP Server.
     *
     * @param command 启动命令(如 {@code mvn})
     * @param args    命令参数列表
     * @throws RuntimeException 当连接失败(无法启动子进程 / 握手失败)
     */
    public StdioMcpToolClient(String command, List<String> args) {
        // 1. 构造子进程参数
        ServerParameters params = ServerParameters.builder(command)
                .args(args.toArray(new String[0]))
                .build();

        // 2. 创建 STDIO 传输层
        StdioClientTransport transport = new StdioClientTransport(
                params, new Jackson2McpJsonMapper());

        // 3. 构建同步客户端
        this.client = McpClient.sync(transport)
                .clientInfo(new McpSchema.Implementation("claude-demo-agent", "0.0.1"))
                .build();

        // 4. 握手
        this.client.initialize();
        log.info("StdioMcpToolClient 初始化完成,已连接 MCP Server");
    }

    @Override
    public String getSchema() {
        try {
            McpSchema.CallToolResult result = client.callTool(
                    new McpSchema.CallToolRequest("get_schema", Map.of()));
            return extractText(result);
        } catch (Exception e) {
            log.warn("getSchema MCP 调用失败: {}", e.toString());
            return "Error: [McpClientException] " + e.getMessage();
        }
    }

    @Override
    public String executeSql(String sql) {
        try {
            McpSchema.CallToolResult result = client.callTool(
                    new McpSchema.CallToolRequest("execute_sql",
                            Map.of("sql", sql)));
            return extractText(result);
        } catch (Exception e) {
            log.warn("executeSql MCP 调用失败: {}", e.toString());
            return "Error: [McpClientException] " + e.getMessage();
        }
    }

    /**
     * 获取 MCP Server 注册的工具列表.
     *
     * <p>用于集成测试验证服务端工具注册状态,不是 {@link McpToolClient} 接口方法.
     *
     * @return 工具名列表
     */
    public List<String> listTools() {
        try {
            McpSchema.ListToolsResult result = client.listTools();
            return result.tools().stream()
                    .map(McpSchema.Tool::name)
                    .toList();
        } catch (Exception e) {
            log.warn("listTools MCP 调用失败: {}", e.toString());
            return List.of();
        }
    }

    @Override
    public void close() {
        try {
            client.closeGracefully();
            log.info("StdioMcpToolClient 已关闭");
        } catch (Exception e) {
            log.warn("StdioMcpToolClient 关闭异常: {}", e.toString());
        }
    }

    /**
     * 从 MCP 工具调用结果中提取文本内容.
     *
     * <p>若结果标记为错误,则返回 {@code "Error: ..."} 格式.
     */
    private static String extractText(McpSchema.CallToolResult result) {
        if (result.isError() != null && result.isError()) {
            return "Error: " + contentText(result);
        }
        return contentText(result);
    }

    private static String contentText(McpSchema.CallToolResult result) {
        List<McpSchema.Content> content = result.content();
        if (content == null || content.isEmpty()) {
            return "";
        }
        // MCP content 可以是 TextContent / ImageContent / EmbeddedResource,
        // V1 只处理 TextContent
        McpSchema.Content first = content.getFirst();
        if (first instanceof McpSchema.TextContent text) {
            return text.text();
        }
        return first.toString();
    }
}
