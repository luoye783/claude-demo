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
    ERROR
}
