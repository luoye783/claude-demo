package com.example.claudedemo.agent.mcp;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP Agent 装配配置.
 *
 * <p>将 {@link McpToolClient} 接口绑定到 {@link StdioMcpToolClient} STDIO 实现,并通过
 * {@code mcp.agent.enabled} 开关控制是否装配 — 默认开启.
 *
 * <p><b>关于 @Bean 返回类型</b>:
 * 故意声明为 {@link StdioMcpToolClient} <b>具体类型</b>而非 {@link McpToolClient} 接口,
 * 这样 Spring 容器里能按具体类查找(便于监控/调试/未来多实现切换),
 * 同时 {@link StdioMcpToolClient} 实现了 {@link McpToolClient} 接口,Spring 注入
 * {@code McpToolClient} 类型的参数时仍能匹配(多态注入).
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
     * 装配 STDIO 实现的 MCP 工具客户端(具体类型 + 多态接口).
     *
     * <p>返回类型用 {@link StdioMcpToolClient} 而非 {@link McpToolClient} 接口 —
     * 容器里的 bean 是 {@code StdioMcpToolClient} 类型(可按具体类查);
     * 同时由于 {@link StdioMcpToolClient} implements {@link McpToolClient},
     * 注入 {@code McpToolClient} 类型参数时 Spring 仍能匹配到本 bean.
     *
     * @param props MCP 客户端配置(命令 + 参数)
     * @return StdioMcpToolClient 实例,Spring 关闭时自动调用 close()
     */
    @Bean(destroyMethod = "close")
    public StdioMcpToolClient mcpToolClient(McpClientProperties props) {
        return new StdioMcpToolClient(props.command(), props.args());
    }
}
