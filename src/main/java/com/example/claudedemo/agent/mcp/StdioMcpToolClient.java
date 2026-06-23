package com.example.claudedemo.agent.mcp;

import com.example.claudedemo.llm.ToolDefinition;
import com.example.claudedemo.mcp.server.Jackson2McpJsonMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于 MCP SDK {@link StdioClientTransport} 的 {@link McpToolClient} 实现.
 *
 * <p>启动一个 MCP Server 子进程(命令与 {@code .mcp.json} 一致),
 * 通过 STDIO 传输层调用其工具.
 *
 * <p><b>生命周期</b>:
 * <ol>
 *   <li>构造器:启动子进程 + 建立 MCP 连接 + 握手</li>
 *   <li>{@link #listToolDefinitions()}: 从 MCP Server 获取工具定义</li>
 *   <li>{@link #callTool(String, String)}: 调用任意 MCP 工具</li>
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

    private static final ObjectMapper MAPPER = new ObjectMapper();

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
    public List<ToolDefinition> listToolDefinitions() {
        try {
            McpSchema.ListToolsResult result = client.listTools();
            return result.tools().stream()
                    .map(StdioMcpToolClient::toToolDefinition)
                    .toList();
        } catch (Exception e) {
            log.warn("listToolDefinitions 失败: {}", e.toString());
            return List.of();
        }
    }

    @Override
    public String callTool(String name, String argumentsJson) {
        try {
            Map<String, Object> args = MAPPER.readValue(
                    argumentsJson == null ? "{}" : argumentsJson,
                    new TypeReference<Map<String, Object>>() {});
            McpSchema.CallToolResult result = client.callTool(
                    new McpSchema.CallToolRequest(name, args));
            return extractText(result);
        } catch (Exception e) {
            log.warn("callTool({}) 失败: {}", name, e.toString());
            return "Error: [McpClientException] " + e.getMessage();
        }
    }

    /**
     * 获取 MCP Server 注册的工具名列表(仅用于集成测试验证).
     */
    public List<String> listToolNames() {
        return listToolDefinitions().stream()
                .map(ToolDefinition::name)
                .toList();
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
     * 将 MCP SDK 的 {@link McpSchema.Tool} 转换为语义层 {@link ToolDefinition}.
     */
    private static ToolDefinition toToolDefinition(McpSchema.Tool tool) {
        return new ToolDefinition(
                tool.name(),
                tool.description() != null ? tool.description() : "",
                convertJsonSchema(tool.inputSchema())
        );
    }

    /**
     * 将 MCP SDK 的 {@link McpSchema.JsonSchema} 转换为 LLM 工具参数定义 Map.
     */
    private static Map<String, Object> convertJsonSchema(McpSchema.JsonSchema schema) {
        Map<String, Object> params = new HashMap<>();
        params.put("type", schema.type());
        if (schema.properties() != null && !schema.properties().isEmpty()) {
            params.put("properties", schema.properties());
        }
        if (schema.required() != null && !schema.required().isEmpty()) {
            params.put("required", schema.required());
        }
        return params;
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
        McpSchema.Content first = content.getFirst();
        if (first instanceof McpSchema.TextContent text) {
            return text.text();
        }
        return first.toString();
    }
}
