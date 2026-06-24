package com.example.claudedemo.agent.mcp;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP Agent 装配配置.
 *
 * <p>将 {@link McpToolClient} 接口绑定到 STDIO 实现,并通过
 * {@code mcp.agent.enabled} 开关控制是否装配 — 默认开启.
 *
 * <p><b>关于 @Bean 而非 @Component</b>:
 * {@link StdioMcpToolClient} 构造器需要 {@code (command, args)} 两个参数,
 * 来自 {@link McpClientProperties};Spring 无法通过字段注入满足这种"二参"签名,
 * 必须在 {@code @Configuration} 中显式 {@code @Bean} 创建.
 *
 * <p><b>关于 destroyMethod = "close"</b>:
 * {@link StdioMcpToolClient} 持有 MCP 子进程句柄,Spring 关闭时会调用
 * {@link StdioMcpToolClient#close()} 释放资源(关闭连接 + 停止子进程).
 *
 * <p><b>启动开销警告</b>:装配此 bean 时会立即 fork MCP server 子进程 + 握手,
 * 耗时数百毫秒到数秒(取决于机器与 {@code mcp.command}/{@code mcp.args} 配置).
 * 在不希望启动 MCP server 的场景(如纯单元测试、纯 LLM 测试)请将
 * {@code mcp.agent.enabled} 设为 {@code false}.
 *
 * @author claude-code
 * @since 0.0.1
 */
@Configuration
@ConditionalOnProperty(name = "mcp.agent.enabled", havingValue = "true", matchIfMissing = true)
public class McpAgentConfig {

    /**
     * 装配 STDIO 实现的 MCP 工具客户端.
     *
     * @param props MCP 客户端配置(命令 + 参数)
     * @return StdioMcpToolClient 实例,Spring 关闭时自动调用 close()
     */
    @Bean(destroyMethod = "close")
    public McpToolClient mcpToolClient(McpClientProperties props) {
        return new StdioMcpToolClient(props.command(), props.args());
    }
}
