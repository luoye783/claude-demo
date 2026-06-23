package com.example.claudedemo.agent.mcp;

/**
 * MCP 工具客户端接口(阶段 A:适配层).
 *
 * <p>抽象 MCP Server 的 get_schema / execute_sql 工具调用,
 * 使 {@link Nl2SqlMcpAgent} 不依赖具体实现(Mock / STDIO / 其他传输).
 *
 * <p><b>契约</b>:
 * <ul>
 *   <li>实现类必须将所有异常吞掉,以 {@code "Error: ..."} 字符串返回</li>
 *   <li>禁止抛出 {@link RuntimeException} 给上层</li>
 * </ul>
 *
 * @author claude-code
 * @since 0.0.1
 */
public interface McpToolClient extends AutoCloseable {

    /**
     * 调用 MCP Server 的 get_schema 工具.
     *
     * @return 数据库 schema 文本;失败时返回 {@code "Error: ..."}
     */
    String getSchema();

    /**
     * 调用 MCP Server 的 execute_sql 工具.
     *
     * @param sql SQL 查询语句(仅 SELECT)
     * @return JSON 格式的结果;失败时返回 {@code "Error: ..."}
     */
    String executeSql(String sql);

    /**
     * 释放资源(如关闭子进程、网络连接).
     */
    @Override
    void close();
}
