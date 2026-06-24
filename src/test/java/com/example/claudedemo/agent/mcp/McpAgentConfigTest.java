package com.example.claudedemo.agent.mcp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link McpAgentConfig} Spring 装配链路测试.
 *
 * <p>用 {@link ApplicationContextRunner} 加载最小化上下文(仅 Jackson + 被测 Config),
 * 不启动 Tomcat / 不连数据库 / 不 fork MCP server 子进程 — 验证配置类的
 * {@code mcp.agent.enabled} 关闭路径行为,跑得快且副作用为零.
 *
 * <p><b>未覆盖场景</b>:{@code mcp.agent.enabled=true} 时 {@link StdioMcpToolClient}
 * 会在 {@code @Bean} factory method 中执行 {@code client.initialize()},这会真连接
 * MCP server,无法在纯单测环境复现.该路径由 {@code mvn spring-boot:run} 端到端验证.
 *
 * @author claude-code
 * @since 0.0.1
 */
class McpAgentConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
            .withUserConfiguration(McpAgentConfig.class);

    @Test
    void should_not_create_stdio_mcp_client_or_mcp_tool_client_when_disabled() {
        runner.withPropertyValues("mcp.agent.enabled=false")
                .run(ctx -> {
                    // 上下文启动成功
                    assertThat(ctx).hasNotFailed();
                    // StdioMcpToolClient Bean 未装配(@ConditionalOnProperty 关闭)
                    assertThat(ctx).doesNotHaveBean(StdioMcpToolClient.class);
                    // McpToolClient 接口 Bean 也未装配(同源)
                    assertThat(ctx).doesNotHaveBean(McpToolClient.class);
                });
    }
}
