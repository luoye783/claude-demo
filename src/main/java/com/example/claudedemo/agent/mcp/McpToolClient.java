package com.example.claudedemo.agent.mcp;

import com.example.claudedemo.llm.ToolDefinition;

import java.util.List;

/**
 * MCP 工具客户端接口.
 *
 * <p>抽象 MCP Server 的工具调用,使 {@link Nl2SqlMcpAgent} 不依赖具体实现(Mock / STDIO / 其他传输).
 * Agent 通过 {@link #listToolDefinitions()} 获取工具列表、通过 {@link #callTool(String, String)}
 * 执行任意工具,对具体工具类型零感知。
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
     * 获取 MCP Server 注册的所有工具定义.
     *
     * <p>返回结果直接用于构造 LLM 的 tools 参数.
     *
     * @return 工具定义列表;失败时返回空列表
     */
    List<ToolDefinition> listToolDefinitions();

    /**
     * 调用 MCP Server 的任意工具.
     *
     * @param name          工具名(如 {@code get_schema})
     * @param argumentsJson LLM 传来的参数 JSON 字符串(如 {@code {"sql":"SELECT 1"}})
     * @return 工具执行结果;失败时返回 {@code "Error: ..."}
     */
    String callTool(String name, String argumentsJson);

    /**
     * 释放资源(如关闭子进程、网络连接).
     */
    @Override
    void close();
}
