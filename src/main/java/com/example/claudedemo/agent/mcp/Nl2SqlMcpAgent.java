package com.example.claudedemo.agent.mcp;

import com.example.claudedemo.agent.ToolCallRecord;
import com.example.claudedemo.agent.ToolCallingExhaustedException;
import com.example.claudedemo.agent.ToolCallingResult;
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
     * @return 包含完整对话历史、工具调用记录与最终答案的结果
     * @throws ToolCallingExhaustedException 当超过 {@value #MAX_ROUNDS} 轮 LLM 仍未给出最终答案
     */
    public ToolCallingResult answer(String question) {
        // 0. 获取工具定义(首次调用时从 McpToolClient 获取)
        List<ToolDefinition> resolvedToolDefs = resolveToolDefs();

        // 1. 初始化消息列表
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("system", SYSTEM_PROMPT));
        messages.add(new ChatMessage("user", question));

        List<ToolCallRecord> toolCalls = new ArrayList<>();

        // 2. 循环
        for (int round = 1; round <= MAX_ROUNDS; round++) {
            LlmResponse resp = llmClient.chatWithTools(messages, resolvedToolDefs);

            // 2.1 无条件回填 assistant 消息
            messages.add(new ChatMessage(
                    "assistant",
                    resp.content(),
                    resp.toolCalls(),
                    null
            ));

            // 2.2 无 tool_calls → LLM 给出最终答案
            List<ToolCall> calls = resp.toolCalls();
            if (calls == null || calls.isEmpty()) {
                log.info("MCP Agent round={} 给出最终答案:{} 字符", round,
                        resp.content() == null ? 0 : resp.content().length());
                return new ToolCallingResult(
                        question,
                        resp.content(),
                        List.copyOf(messages),
                        ToolCallRecord.copyOf(toolCalls),
                        round
                );
            }

            // 2.3 有 tool_calls → 逐个执行
            for (ToolCall call : calls) {
                String result = executeTool(call);
                messages.add(new ChatMessage("tool", result, null, call.id()));
                toolCalls.add(ToolCallRecord.of(call, result));
                log.info("MCP Agent round={} 通过 McpToolClient 调用 {} -> {} 字符",
                        round, call.function().name(), result.length());
            }
        }

        // 3. 超过最大轮数
        log.error("MCP Agent 失败:已执行 {} 轮,LLM 始终未给出最终答案", MAX_ROUNDS);
        throw new ToolCallingExhaustedException(
                MAX_ROUNDS, List.copyOf(messages), ToolCallRecord.copyOf(toolCalls));
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
}
