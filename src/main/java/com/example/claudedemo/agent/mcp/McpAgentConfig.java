package com.example.claudedemo.agent.mcp;

import com.example.claudedemo.agent.planner.AgentPlanner;
import com.example.claudedemo.agent.planner.PlannerProperties;
import com.example.claudedemo.agent.planner.SimpleAgentPlanner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP Agent 装配配置.
 *
 * <p>将 {@link McpToolClient} 接口绑定到 {@link StdioMcpToolClient} STDIO 实现,并通过
 * {@code mcp.agent.enabled} 开关控制是否装配 — 默认开启.
 *
 * <p><b>V3 新增</b>:Planner 条件装配({@code agent.planner.enabled=true}) 和
 * {@link PlannerProperties} 配置绑定。
 *
 * @since 0.0.1
 */
@Configuration
@ConditionalOnProperty(name = "mcp.agent.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(PlannerProperties.class)
public class McpAgentConfig {

    /**
     * 装配 STDIO 实现的 MCP 工具客户端(具体类型 + 多态接口).
     */
    @Bean(destroyMethod = "close")
    public StdioMcpToolClient mcpToolClient(McpClientProperties props) {
        return new StdioMcpToolClient(props.command(), props.args());
    }

    /**
     * Planner 条件装配(V3 新增).
     *
     * <p>仅在 {@code agent.planner.enabled=true} 时创建 SimpleAgentPlanner bean.
     */
    @Bean
    @ConditionalOnProperty(name = "agent.planner.enabled", havingValue = "true")
    public AgentPlanner simpleAgentPlanner() {
        return new SimpleAgentPlanner();
    }
}
