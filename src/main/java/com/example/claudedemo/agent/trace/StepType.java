package com.example.claudedemo.agent.trace;

/**
 * Agent Trace 步骤类型.
 *
 * @author claude-code
 * @since 0.0.1
 */
public enum StepType {

    /** 用户提问. */
    USER_QUESTION,

    /** LLM 请求发送. */
    LLM_REQUEST,

    /** LLM 响应接收. */
    LLM_RESPONSE,

    /** 工具调用请求. */
    TOOL_CALL,

    /** 工具调用结果. */
    TOOL_RESULT,

    /** 最终答案. */
    FINAL_ANSWER,

    /** 错误. */
    ERROR,

    /**
     * 会话记忆压缩(V2 第四阶段).
     *
     * <p>由 {@link com.example.claudedemo.agent.memory.InMemoryConversationStore}
     * 在 turn 数超阈值时触发;content 包含被淘汰 turn 数、生成摘要字符数等。
     */
    MEMORY_COMPRESS,

    /**
     * RAG 检索(V2 第六阶段 RAG V1).
     *
     * <p>由 {@link com.example.claudedemo.agent.mcp.Nl2SqlMcpAgent} 在拼装 LLM
     * messages 前调用 {@link com.example.claudedemo.agent.rag.RagRetriever} 后记录;
     * content 含 query、topK、命中数、耗时。注意:当 {@code RagRetriever} 未装配时
     * <b>不记录</b>此步骤,保持老 trace 形状兼容。
     */
    RAG_RETRIEVE
}
