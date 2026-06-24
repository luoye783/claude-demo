package com.example.claudedemo.agent.mcp;

import com.example.claudedemo.agent.ToolCallRecord;
import com.example.claudedemo.agent.ToolCallingExhaustedException;
import com.example.claudedemo.agent.ToolCallingResult;
import com.example.claudedemo.agent.trace.AgentTrace;
import com.example.claudedemo.agent.trace.StepType;
import com.example.claudedemo.agent.trace.TraceStep;
import com.example.claudedemo.llm.ChatMessage;
import com.example.claudedemo.llm.LlmClient;
import com.example.claudedemo.llm.LlmResponse;
import com.example.claudedemo.llm.ToolCall;
import com.example.claudedemo.llm.ToolDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * NL2SQL MCP Agent V1 — 通过 {@link McpToolClient} 调用 MCP Server 工具.
 *
 * <p>主流程与 {@link com.example.claudedemo.agent.Nl2SqlToolAgent Nl2SqlToolAgent} 一致：
 * 维护 {@code messages} 列表 → 循环调 LLM(带 tools) → 若 LLM 返回
 * {@code tool_calls} 则通过 McpToolClient 逐个执行并以 {@code role=tool} 追加结果 → 若
 * LLM 不再请求工具则将 content 作为最终答案返回。
 *
 * <p><b>与 Nl2SqlToolAgent 的关键差异</b>：
 * <ul>
 *   <li>工具定义通过 {@link McpToolClient#listToolDefinitions()} 动态获取,而非硬编码或本地 AgentTool</li>
 *   <li>工具执行通过 {@link McpToolClient#callTool(String, String)} 通用转发,无 switch/case 枚举</li>
 *   <li>不依赖 Spring 注解{@code @Component}(由调用方自己实例化)</li>
 * </ul>
 *
 * <p><b>关键不变量</b>：
 * <ul>
 *   <li>assistant 消息必须无条件回填(OpenAI 协议要求 tool 消息前必须对应 assistant)</li>
 *   <li>每轮 LLM 调用后,若存在 tool_calls,严格按 OpenAI 顺序追加 tool 消息(tool_call_id 匹配)</li>
 *   <li>最多循环 {@value #MAX_ROUNDS} 轮,防止 LLM 反复调用工具的死循环</li>
 *   <li>McpToolClient 应自行吞掉异常、返回 {@code "Error: ..."} 字符串,循环不被打断</li>
 * </ul>
 *
 * @author claude-code
 * @since 0.0.1
 */
public class Nl2SqlMcpAgent {

    private static final Logger log = LoggerFactory.getLogger(Nl2SqlMcpAgent.class);

    /** 最大循环轮数(每轮 = 1 次 LLM 调用). */
    public static final int MAX_ROUNDS = 5;

    /** trace 中工具结果展示的最大字符数. */
    private static final int TOOL_RESULT_TRACE_LIMIT = 200;

    private static final String SYSTEM_PROMPT = """
            你是 NL2SQL 助手。你可以使用工具查询数据库,然后用中文回答用户的问题。
            工具使用规则:
            1. 必须先调用 get_schema 获取数据库表结构
            2. 再调用 execute_sql 执行只读 SQL
            3. 严格基于工具返回的实际数据作答,不要捏造表名/字段名/数值
            4. 当工具返回 Error 字符串时,必须修改 SQL 或策略后再次调用,直到成功或信息充分
            """;

    private final LlmClient llmClient;
    private final McpToolClient mcpToolClient;
    /** 工具定义缓存(首次调用时从 McpToolClient 获取,后续复用). */
    private List<ToolDefinition> toolDefs;

    public Nl2SqlMcpAgent(LlmClient llmClient, McpToolClient mcpToolClient) {
        this.llmClient = llmClient;
        this.mcpToolClient = mcpToolClient;
    }

    /**
     * 回答一个自然语言问题.
     *
     * @param question 用户问题
     * @return 包含完整对话历史、工具调用记录、最终答案与执行 trace 的结果
     * @throws ToolCallingExhaustedException 当超过 {@value #MAX_ROUNDS} 轮 LLM 仍未给出最终答案
     */
    public ToolCallingResult answer(String question) {
        // 0. 初始化 trace,并在入口记录用户问题
        AgentTrace trace = new AgentTrace();
        trace.addStep(StepType.USER_QUESTION, question);

        // 1. 获取工具定义(首次调用时从 McpToolClient 获取)
        List<ToolDefinition> resolvedToolDefs = resolveToolDefs();

        // 2. 初始化消息列表
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("system", SYSTEM_PROMPT));
        messages.add(new ChatMessage("user", question));

        List<ToolCallRecord> toolCalls = new ArrayList<>();

        // 3. 循环
        for (int round = 1; round <= MAX_ROUNDS; round++) {
            // 3.0 LLM 请求
            trace.addStep(StepType.LLM_REQUEST, "round=" + round);

            long llmStart = System.currentTimeMillis();
            LlmResponse resp = llmClient.chatWithTools(messages, resolvedToolDefs);
            long llmDuration = System.currentTimeMillis() - llmStart;

            // 3.1 无条件回填 assistant 消息
            messages.add(new ChatMessage(
                    "assistant",
                    resp.content(),
                    resp.toolCalls(),
                    null
            ));

            // 3.2 记录 LLM 响应
            String respContent = (resp.content() == null || resp.content().isEmpty())
                    ? "(tool call)"
                    : resp.content();
            trace.addStep(StepType.LLM_RESPONSE, respContent, llmDuration);

            // 3.3 无 tool_calls → LLM 给出最终答案
            List<ToolCall> calls = resp.toolCalls();
            if (calls == null || calls.isEmpty()) {
                log.info("MCP Agent round={} 给出最终答案:{} 字符", round,
                        resp.content() == null ? 0 : resp.content().length());
                trace.addStep(StepType.FINAL_ANSWER, resp.content());
                return new ToolCallingResult(
                        question,
                        resp.content(),
                        List.copyOf(messages),
                        ToolCallRecord.copyOf(toolCalls),
                        round,
                        trace
                );
            }

            // 3.4 有 tool_calls → 逐个执行
            for (ToolCall call : calls) {
                String callLabel = describeCall(call);
                trace.addStep(StepType.TOOL_CALL, callLabel);

                long toolStart = System.currentTimeMillis();
                String result = executeTool(call);
                long toolDuration = System.currentTimeMillis() - toolStart;

                messages.add(new ChatMessage("tool", result, null, call.id()));
                toolCalls.add(ToolCallRecord.of(call, result));
                log.info("MCP Agent round={} 通过 McpToolClient 调用 {} -> {} 字符",
                        round, call.function().name(), result.length());

                trace.addStep(StepType.TOOL_RESULT, summarizeForTrace(result), toolDuration);
            }
        }

        // 4. 超过最大轮数
        log.error("MCP Agent 失败:已执行 {} 轮,LLM 始终未给出最终答案", MAX_ROUNDS);
        trace.addError("超过最大轮数 " + MAX_ROUNDS + ",LLM 始终未给出最终答案");
        throw new ToolCallingExhaustedException(
                MAX_ROUNDS,
                List.copyOf(messages),
                ToolCallRecord.copyOf(toolCalls),
                trace
        );
    }

    /**
     * 通过 {@link McpToolClient} 通用调用.
     *
     * <p>不做任何工具名/参数的枚举或校验,完全交由 McpToolClient 处理.
     */
    private String executeTool(ToolCall call) {
        if (call == null || call.function() == null) {
            return "Error: [MalformedCall] tool call is malformed";
        }
        String args = call.function().arguments();
        return mcpToolClient.callTool(call.function().name(), args == null ? "{}" : args);
    }

    /**
     * 获取工具定义.
     *
     * <p>首次调用时向 McpToolClient 查询并缓存,后续复用.
     */
    private List<ToolDefinition> resolveToolDefs() {
        if (toolDefs == null) {
            toolDefs = mcpToolClient.listToolDefinitions();
        }
        return toolDefs;
    }

    /**
     * 生成工具调用 trace 摘要,格式 {@code "name args"}.
     */
    private String describeCall(ToolCall call) {
        if (call == null || call.function() == null) {
            return "<malformed>";
        }
        String name = call.function().name();
        String args = call.function().arguments();
        if (args == null) {
            return name + " {}";
        }
        if (args.length() <= TOOL_RESULT_TRACE_LIMIT) {
            return name + " " + args;
        }
        return name + " " + args.substring(0, TOOL_RESULT_TRACE_LIMIT) + "...";
    }

    /**
     * trace 中工具结果摘要,过长截断到 {@value #TOOL_RESULT_TRACE_LIMIT} 字符.
     * <p>实际写入 {@link TraceStep} 时还会被 {@link TraceStep} 自身的 500 字符截断兜底.
     */
    private String summarizeForTrace(String result) {
        if (result == null) {
            return "";
        }
        if (result.length() <= TOOL_RESULT_TRACE_LIMIT) {
            return result;
        }
        return result.substring(0, TOOL_RESULT_TRACE_LIMIT) + "...";
    }
}
