package com.example.claudedemo.agent.tools;

import com.example.claudedemo.llm.ToolDefinition;

/**
 * Agent 工具接口.
 *
 * <p>每个实现对应 LLM 可调用的一项能力(V1: get_schema、execute_sql)。
 *
 * <p><b>协议</b>:
 * <ul>
 *   <li>{@link #definition()} 描述工具(给 LLM 看)</li>
 *   <li>{@link #execute(String)} 实际执行(Java 调用,LLM 不可见)</li>
 * </ul>
 *
 * <p><b>不变量</b>(execute 必须遵守):
 * <ul>
 *   <li>任何异常必须被吞掉,转成 {@code "Error: [...] ..."} 字符串返回(便于 LLM 自我修复)</li>
 *   <li>不得抛出 RuntimeException 给上层,否则会打断 Agent 循环</li>
 *   <li>返回值必须是字符串,直接进入 tool 消息的 content 字段</li>
 * </ul>
 *
 * @author claude-code
 * @since 0.0.1
 */
public interface AgentTool {

    /**
     * 工具定义(给 LLM 看的元数据).
     */
    ToolDefinition definition();

    /**
     * 执行工具.
     *
     * @param argumentsJson LLM 传来的参数(JSON 字符串,符合 {@link ToolDefinition#parameters()})
     * @return 工具执行结果(字符串,直接进入 tool 消息 content);失败时返回 {@code "Error: ..."} 形式
     */
    String execute(String argumentsJson);
}
