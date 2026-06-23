package com.example.claudedemo.mcp.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link McpServerMetadata} 纯常量校验,防止有人误改字符串.
 *
 * @author claude-code
 * @since 0.0.1
 */
class McpServerMetadataTest {

    @Test
    void serverNameMatchesMcpJsonKey() {
        // 与 .mcp.json 中的 server key 保持一致
        // 改动会破坏 Claude Code 端配置,必须显式确认
        assertEquals("claude-demo-db", McpServerMetadata.SERVER_NAME);
    }

    @Test
    void serverVersionMatchesProjectVersion() {
        // 与项目版本绑定,后续可随 release 调整
        assertEquals("0.0.1", McpServerMetadata.SERVER_VERSION);
    }
}
