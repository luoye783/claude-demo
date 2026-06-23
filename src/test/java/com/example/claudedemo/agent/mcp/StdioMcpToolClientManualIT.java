package com.example.claudedemo.agent.mcp;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link StdioMcpToolClient} 手动集成测试：连接真实 MCP Server.
 *
 * <p>默认 {@code mvn test} 会<b>自动跳过</b>。运行命令：
 * <pre>{@code
 * RUN_MCP_IT=true mvn test -Dtest=StdioMcpToolClientManualIT
 * }</pre>
 *
 * <p>前置条件：
 * <ul>
 *   <li>项目已 {@code mvn compile}</li>
 *   <li>MySQL {@code claude_demo} 库可访问</li>
 *   <li>网络(GitHub / Maven 镜像)能下载 MCP SDK 依赖</li>
 * </ul>
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_MCP_IT", matches = "true")
class StdioMcpToolClientManualIT {

    @Autowired
    private McpClientProperties mcpClientProperties;

    @Test
    void listTools_should_contain_getSchema_and_executeSql() throws Exception {
        try (StdioMcpToolClient client = new StdioMcpToolClient(
                mcpClientProperties.command(), mcpClientProperties.args())) {

            List<String> tools = client.listTools();

            System.out.println("=== listTools ===");
            tools.forEach(t -> System.out.println("  - " + t));

            assertTrue(tools.contains("get_schema"), "应注册 get_schema");
            assertTrue(tools.contains("execute_sql"), "应注册 execute_sql");
            assertFalse(tools.isEmpty(), "工具列表不应为空");
        }
    }

    @Test
    void getSchema_should_return_schema_text() throws Exception {
        try (StdioMcpToolClient client = new StdioMcpToolClient(
                mcpClientProperties.command(), mcpClientProperties.args())) {

            String schema = client.getSchema();

            System.out.println("=== getSchema ===");
            System.out.println(schema);

            assertFalse(schema.isEmpty(), "schema 不应为空");
            assertFalse(schema.startsWith("Error:"), "不应返回错误,实际: " + schema);
        }
    }

    @Test
    void executeSql_select_should_return_json() throws Exception {
        try (StdioMcpToolClient client = new StdioMcpToolClient(
                mcpClientProperties.command(), mcpClientProperties.args())) {

            String result = client.executeSql("SELECT 1");

            System.out.println("=== executeSql SELECT 1 ===");
            System.out.println(result);

            assertFalse(result.startsWith("Error:"), "不应返回错误,实际: " + result);
            assertTrue(result.contains("rowCount"), "应包含 rowCount 字段");
            assertTrue(result.contains("rows"), "应包含 rows 字段");
        }
    }

    @Test
    void executeSql_delete_should_be_rejected() throws Exception {
        try (StdioMcpToolClient client = new StdioMcpToolClient(
                mcpClientProperties.command(), mcpClientProperties.args())) {

            String result = client.executeSql("DELETE FROM users");

            System.out.println("=== executeSql DELETE (expected error) ===");
            System.out.println(result);

            assertTrue(result.startsWith("Error:"), "DELETE 应返回错误,实际: " + result);
            assertTrue(result.contains("DML"), "错误信息应包含 DML 提示");
        }
    }
}
