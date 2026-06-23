package com.example.claudedemo.mcp.server;

import com.example.claudedemo.agent.SchemaSelector;
import com.example.claudedemo.sql.SchemaIntrospector;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * MCP Server 专用 Spring 配置.
 *
 * <p><b>为什么不用 {@code @SpringBootApplication}</b>:
 * 如果本类用 {@code @SpringBootApplication},其 {@code exclude} / {@code scanBasePackages} /
 * 其它元数据会被记入 Spring Boot 的 <i>主配置候选集</i>.当其它测试(如 {@code LlmClientTest})
 * 启动 {@code ClaudeDemoApplication} 时,Spring Boot Test 会扫描整个 classpath 找
 * {@code @SpringBootApplication},把它们各自的元数据合并 —— {@code McpServerMain} 的
 * {@code exclude} 会污染 LlmClientTest 的上下文,导致 {@code DataSourceAutoConfiguration} 被排除、
 * 测试报 <i>"No qualifying bean of type DataSource"</i>.
 *
 * <p>本类用最朴素的 {@code @Configuration} + {@code @ComponentScan},不参与 Spring Boot 自动配置,
 * 不被 Spring Boot Test 当作候选主类,对其它上下文零影响.
 *
 * <p><b>Step 2 范围内</b>:引入 {@link McpDataSourceConfig}(H2 嵌入式数据源)
 * 与 {@link SchemaIntrospector} / {@link SchemaSelector},使 get_schema tool 可工作.
 * 不扫描 {@code com.example.claudedemo.agent} / {@code com.example.claudedemo.sql} 全包,
 * 而是精准 {@code @Import} 需要的 bean,避免拉入不相关的组件(如 LlmClient 等).
 *
 * @author claude-code
 * @since 0.0.1
 */
@Configuration
@ComponentScan(basePackages = "com.example.claudedemo.mcp")
@Import({McpDataSourceConfig.class, SchemaIntrospector.class, SchemaSelector.class})
public class McpServerConfig {
}
