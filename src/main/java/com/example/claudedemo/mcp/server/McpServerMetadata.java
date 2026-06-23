package com.example.claudedemo.mcp.server;

/**
 * MCP Server 元数据常量.
 *
 * <p>统一维护 server 标识,避免在多个文件中硬编码.
 *
 * @author claude-code
 * @since 0.0.1
 */
public final class McpServerMetadata {

    /**
     * MCP server 名称(Claude Code {@code /mcp} 列表中显示).
     *
     * <p>与 {@code .mcp.json} 中的 key 保持一致,便于人工核对.
     */
    public static final String SERVER_NAME = "claude-demo-db";

    /** MCP server 版本(随项目版本走). */
    public static final String SERVER_VERSION = "0.0.1";

    private McpServerMetadata() {
        // 工具类,禁止实例化
    }
}
