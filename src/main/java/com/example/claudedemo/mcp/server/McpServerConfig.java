package com.example.claudedemo.mcp.server;

import com.example.claudedemo.agent.SchemaSelector;
import com.example.claudedemo.sql.SchemaIntrospector;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
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
 * <p>本类用最朴素的 {@code @Configuration} + {@code @ComponentScan} + {@code @EnableAutoConfiguration},
 * 不被 Spring Boot Test 当作候选主类,对其它上下文零影响.
 *
 * <p>{@code @EnableAutoConfiguration} 使 {@code DataSourceAutoConfiguration} 自动生效,
 * 从 {@code application.yml} 的 {@code spring.datasource.*} 属性创建 MySQL 数据源.
 * 通过 {@code @Import} 精准引入 {@link SchemaIntrospector} / {@link SchemaSelector},
 * 避免拉入不相关的组件(如 LlmClient 等).
 *
 * @author claude-code
 * @since 0.0.1
 */
@Configuration
@EnableAutoConfiguration
@ComponentScan(basePackages = "com.example.claudedemo.mcp")
@Import({SchemaIntrospector.class, SchemaSelector.class})
public class McpServerConfig {
}
