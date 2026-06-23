package com.example.claudedemo.mcp.server;

import io.modelcontextprotocol.server.McpSyncServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * {@link McpServerFactory} Step 1 单测.
 *
 * <p>验证:
 * <ul>
 *   <li>工厂能构造且 {@code start()} 不抛</li>
 *   <li>返回的 server 已启动,工具列表为空</li>
 *   <li>server 能被 close</li>
 * </ul>
 *
 * <p>注意:transport 绑定的 {@code System.in} 在测试中处于"无数据"状态,
 * 内部读取线程会空转;本测试不与协议交互,仅验证构造路径.
 *
 * @author claude-code
 * @since 0.0.1
 */
class McpServerFactoryTest {

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void start_buildsEmptyServer() {
        McpServerFactory factory = new McpServerFactory();

        McpSyncServer server = assertDoesNotThrow(factory::start,
                "factory.start() 不应抛异常");

        assertNotNull(server, "返回的 server 不应为 null");
        assertEquals(0, server.listTools().size(),
                "Step 1 工具列表应为空");

        // 清理:close server,释放后台线程
        assertDoesNotThrow(server::close, "server.close() 不应抛异常");
    }
}
