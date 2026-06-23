package com.example.claudedemo.mcp.server;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

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
 * <p><b>Step 1 范围内</b>:不引入任何 Spring Boot 自动配置(不需要 DataSource / Web);
 * Step 3 引入 {@code execute_sql} tool 时,会单独增加 DataSource 配置类,继续保持本类的最小化.
 *
 * @author claude-code
 * @since 0.0.1
 */
@Configuration
@ComponentScan(basePackages = "com.example.claudedemo.mcp")
public class McpServerConfig {
}
