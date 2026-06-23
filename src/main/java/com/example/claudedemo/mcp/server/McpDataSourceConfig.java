package com.example.claudedemo.mcp.server;

import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * MCP Server 数据源配置.
 *
 * <p>提供 MySQL 数据源,让 {@link com.example.claudedemo.sql.SchemaIntrospector SchemaIntrospector}
 * 与 {@link com.example.claudedemo.agent.SchemaSelector SchemaSelector} 在 MCP 上下文中可用,
 * 复用主应用的 {@code claude_demo} 库.
 *
 * <p><b>为什么不依赖 Spring Boot 自动配置</b>:
 * MCP 上下文由 {@code SpringApplicationBuilder.sources(McpServerConfig.class)} 启动,
 * 未开启 {@code @EnableAutoConfiguration},因此手动声明此 DataSource bean.
 *
 * @author claude-code
 * @since 0.0.1
 */
@Configuration
public class McpDataSourceConfig {

    /**
     * MySQL 数据源,连接 {@code claude_demo} 库.
     *
     * @return MySQL HikariCP DataSource
     */
    @Bean
    public DataSource dataSource() {
        return DataSourceBuilder.create()
                .driverClassName("com.mysql.cj.jdbc.Driver")
                .url("jdbc:mysql://localhost:3306/claude_demo"
                        + "?useUnicode=true&characterEncoding=UTF-8"
                        + "&connectionCollation=utf8mb4_unicode_ci"
                        + "&serverTimezone=Asia/Shanghai"
                        + "&useSSL=false&allowPublicKeyRetrieval=true")
                .username("root")
                .password("123456")
                .build();
    }
}
