package com.example.claudedemo.mcp.server;

import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;

/**
 * MCP Server 数据源配置.
 *
 * <p>从 {@code application.yml} 读取 {@code spring.datasource.*} 参数,
 * 手动创建 MySQL 数据源.
 *
 * <p><b>为什么不依赖 Spring Boot 自动配置</b>:
 * MCP 上下文由 {@code SpringApplicationBuilder.sources(McpServerConfig.class)} 启动,
 * 未开启 {@code @EnableAutoConfiguration},因此手动创建此 DataSource bean.
 * 配置值通过 {@link Environment} 从 Spring 属性源(含 {@code application.yml})获取,
 * 无需硬编码.
 *
 * @author claude-code
 * @since 0.0.1
 */
@Configuration
public class McpDataSourceConfig {

    /**
     * MySQL 数据源,连接 {@code claude_demo} 库.
     *
     * @param env Spring Environment,自动注入属性源中的配置值
     * @return MySQL HikariCP DataSource
     */
    @Bean
    public DataSource dataSource(Environment env) {
        return DataSourceBuilder.create()
                .driverClassName(env.getProperty("spring.datasource.driver-class-name"))
                .url(env.getProperty("spring.datasource.url"))
                .username(env.getProperty("spring.datasource.username"))
                .password(env.getProperty("spring.datasource.password"))
                .build();
    }
}
