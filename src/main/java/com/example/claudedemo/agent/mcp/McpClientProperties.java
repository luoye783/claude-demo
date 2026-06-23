package com.example.claudedemo.agent.mcp;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * MCP 客户端配置属性.
 *
 * <p>从 {@code application.yml} 读取 {@code mcp.command} 和 {@code mcp.args},
 * 用于 {@link StdioMcpToolClient} 启动 MCP Server 子进程.
 *
 * @param command 启动命令(如 {@code mvn})
 * @param args    命令参数列表
 * @author claude-code
 * @since 0.0.1
 */
@ConfigurationProperties(prefix = "mcp")
public record McpClientProperties(
        String command,
        List<String> args
) {
}
