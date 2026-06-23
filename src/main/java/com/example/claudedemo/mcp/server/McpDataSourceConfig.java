package com.example.claudedemo.mcp.server;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;

/**
 * MCP Server 数据源配置(开发/测试环境).
 *
 * <p>使用 H2 内存库,让 {@link com.example.claudedemo.sql.SchemaIntrospector SchemaIntrospector}
 * 与 {@link com.example.claudedemo.agent.SchemaSelector SchemaSelector} 在 MCP 上下文中可用.
 *
 * <p><b>生产环境</b>:应通过 {@code application-mcp.yml} 或 {@code @Profile("prod")}
 * 替换为 MySQL 数据源,引入 {@code mysql-connector-j} 依赖并去掉 H2.
 *
 * @author claude-code
 * @since 0.0.1
 */
@Configuration
public class McpDataSourceConfig {

    /**
     * MCP 上下文专用的嵌入式 H2 数据源.
     *
     * <p>测试场景中 {@code @JdbcTest} 会通过 {@code @AutoConfigureTestDatabase} 替换此 bean,
     * 不产生冲突.
     *
     * @return 嵌入式 H2 DataSource(表由业务方自行 DDL)
     */
    @Bean
    public DataSource dataSource() {
        return new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .build();
    }
}
