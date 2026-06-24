package com.example.claudedemo.agent;

import com.example.claudedemo.agent.tools.AgentTool;
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
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * NL2SQL Tool Calling Agent V1.
 *
 * <p>主流程：维护 {@code messages} 列表 → 循环调 LLM(带 tools) → 若 LLM 返回
 * {@code tool_calls} 则按 id 逐个执行工具并以 {@code role=tool} 追加结果 → 若
 * LLM 不再请求工具则将 content 作为最终答案返回。
 *
 * <p><b>关键不变量</b>：
 * <ul>
 *   <li>assistant 消息必须无条件回填(OpenAI 协议要求 tool 消息前必须对应 assistant)</li>
 *   <li>每轮 LLM 调用后,若存在 tool_calls,严格按 OpenAI 顺序追加 tool 消息(tool_call_id 匹配)</li>
 *   <li>最多循环 {@value #MAX_ROUNDS} 轮,防止 LLM 反复调用工具的死循环</li>
 *   <li>工具执行异常被工具自身吞掉、转成 {@code "Error: ..."} 字符串回传,循环不被打断</li>
 *   <li>execute_sql 内部必经 {@link com.example.claudedemo.sql.SqlValidator} + {@link com.example.claudedemo.sql.SqlExecutor}</li>
 * </ul>
 *
 * <p><b>与 V1 关系</b>:本类为 V1(固定两轮)的并行实现,二者并存,互不影响;
 * 调用方根据需要选择 V1 简单流程或 V2 工具调用流程。
 *
 * @author claude-code
 * @since 0.0.1
 */
@Component
public class Nl2SqlToolAgent {

    private static final Logger log = LoggerFactory.getLogger(Nl2SqlToolAgent.class);

    /** 最大循环轮数(每轮 = 1 次 LLM 调用),超过此上限抛 {@link ToolCallingExhaustedException}. */
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
    private final List<AgentTool> tools;
    private final Map<String, AgentTool> toolMap;

    public Nl2SqlToolAgent(LlmClient llmClient, List<AgentTool> tools) {
        this.llmClient = llmClient;
        this.tools = List.copyOf(tools);
        this.toolMap = new HashMap<>();
        for (AgentTool t : this.tools) {
            // 同名工具以先注册者为准(避免重复注入导致歧义)
            toolMap.putIfAbsent(t.definition().name(), t);
        }
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

        // 1. 初始化消息列表
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("system", SYSTEM_PROMPT));
        messages.add(new ChatMessage("user", question));

        // 2. 工具定义(每轮都发,供 LLM 重新选择)
        List<ToolDefinition> toolDefs = tools.stream()
                .map(AgentTool::definition)
                .toList();

        List<ToolCallRecord> toolCalls = new ArrayList<>();

        // 3. 循环
        for (int round = 1; round <= MAX_ROUNDS; round++) {
            // 3.0 LLM 请求
            trace.addStep(StepType.LLM_REQUEST, "round=" + round);

            long llmStart = System.currentTimeMillis();
            LlmResponse resp = llmClient.chatWithTools(messages, toolDefs);
            long llmDuration = System.currentTimeMillis() - llmStart;

            // 3.1 无条件回填 assistant 消息(OpenAI 协议要求 tool 消息前必须有 assistant)
            messages.add(new ChatMessage(
                    "assistant",
                    resp.content(),
                    resp.toolCalls(),
                    null
            ));

            // 3.2 记录 LLM 响应(content 文本或工具调用摘要)
            String respContent = (resp.content() == null || resp.content().isEmpty())
                    ? "(tool call)"
                    : resp.content();
            trace.addStep(StepType.LLM_RESPONSE, respContent, llmDuration);

            // 3.3 无 tool_calls → LLM 给出最终答案
            List<ToolCall> calls = resp.toolCalls();
            if (calls == null || calls.isEmpty()) {
                log.info("Tool Calling round={} 给出最终答案:{} 字符", round,
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

            // 3.4 有 tool_calls → 逐个执行,按 OpenAI 顺序追加 tool 消息
            for (ToolCall call : calls) {
                String callLabel = describeCall(call);
                trace.addStep(StepType.TOOL_CALL, callLabel);

                long toolStart = System.currentTimeMillis();
                String result = executeTool(call);
                long toolDuration = System.currentTimeMillis() - toolStart;

                messages.add(new ChatMessage("tool", result, null, call.id()));
                toolCalls.add(ToolCallRecord.of(call, result));
                log.info("Tool Calling round={} 调用 {} -> {} 字符", round, call.function().name(),
                        result.length());

                trace.addStep(StepType.TOOL_RESULT, summarizeForTrace(result), toolDuration);
            }
        }

        // 4. 超过最大轮数
        log.error("Tool Calling 失败:已执行 {} 轮,LLM 始终未给出最终答案,共 {} 次工具调用", MAX_ROUNDS, toolCalls.size());
        trace.addError("超过最大轮数 " + MAX_ROUNDS + ",LLM 始终未给出最终答案");
        throw new ToolCallingExhaustedException(
                MAX_ROUNDS,
                List.copyOf(messages),
                ToolCallRecord.copyOf(toolCalls),
                trace
        );
    }

    /**
     * 分发工具调用.
     *
     * <p>三层防御:
     * <ul>
     *   <li>工具名不存在 → 返回 {@code Error: unknown tool} 字符串</li>
     *   <li>工具实现抛出异常 → 工具自身应已捕获;此处再兜一次,绝不外抛</li>
     *   <li>任何意外 → 返回 {@code Error: ...} 字符串,保证循环不被打断</li>
     * </ul>
     */
    private String executeTool(ToolCall call) {
        if (call == null || call.function() == null) {
            return "Error: [MalformedCall] tool call is malformed";
        }
        String name = call.function().name();
        String args = call.function().arguments();

        AgentTool tool = toolMap.get(name);
        if (tool == null) {
            return "Error: [UnknownTool] unknown tool '" + name + "'";
        }
        try {
            return tool.execute(args == null ? "{}" : args);
        } catch (Exception e) {
            // 工具实现理论上应自行处理异常,此处为防御性兜底
            log.warn("工具 {} 执行异常被兜底: {}", name, e.toString());
            return "Error: [" + e.getClass().getSimpleName() + "] " + e.getMessage();
        }
    }

    /**
     * 生成工具调用 trace 摘要,格式 {@code "name args"}.args 过长会被截断以避免污染 trace.
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
     * <p>实际写入 {@link TraceStep} 时还会再被 {@link TraceStep} 自身的
     * 500 字符截断兜底,这里是业务级可读性优化。
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
