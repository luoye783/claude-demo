package com.example.claudedemo.agent;

import com.example.claudedemo.agent.trace.AgentTrace;
import com.example.claudedemo.llm.ChatMessage;

import java.util.List;

/**
 * Tool Calling Agent 单次执行的完整结果.
 *
 * <p>与 V1 AgentResult 并列;V1 保持不变,本类专供 Nl2SqlToolAgent。
 *
 * <p><b>调试便利</b>:同时暴露 messages 完整对话历史与 toolCalls 扁平工具调用列表,
 * 排查时可直接读取 messages 看每轮 LLM 输入输出,或读取 toolCalls 快速核对工具行为。
 *
 * @param question   用户原始问题
 * @param answer     LLM 给出的最终自然语言答案(content 非空、无 tool_calls 的最后一轮)
 * @param messages   完整对话历史(系统提示 + 用户问题 + 若干轮 assistant/tool)
 * @param toolCalls  所有工具调用记录(含工具名、入参、Java 执行结果),按执行顺序
 * @param rounds     实际进行的 LLM 调用轮数(1 ≤ rounds ≤ {@value Nl2SqlToolAgent#MAX_ROUNDS})
 * @param trace      完整执行轨迹(含 LLM 请求/响应耗时、工具调用耗时等)
 * @author claude-code
 * @since 0.0.1
 */
public record ToolCallingResult(
        String question,
        String answer,
        List<ChatMessage> messages,
        List<ToolCallRecord> toolCalls,
        int rounds,
        AgentTrace trace
) {

    /**
     * V1 兼容构造器:不携带 trace.
     */
    public ToolCallingResult(String question, String answer, List<ChatMessage> messages,
                             List<ToolCallRecord> toolCalls, int rounds) {
        this(question, answer, messages, toolCalls, rounds, new AgentTrace());
    }
}
