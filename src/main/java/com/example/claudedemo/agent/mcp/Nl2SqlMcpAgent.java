package com.example.claudedemo.agent.mcp;

import com.example.claudedemo.agent.ToolCallRecord;
import com.example.claudedemo.agent.ToolCallingExhaustedException;
import com.example.claudedemo.agent.ToolCallingResult;
import com.example.claudedemo.llm.ChatMessage;
import com.example.claudedemo.llm.LlmClient;
import com.example.claudedemo.llm.LlmResponse;
import com.example.claudedemo.llm.ToolCall;
import com.example.claudedemo.llm.ToolDefinition;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
 *   <li>工具定义硬编码(而非从 AgentTool 获取)</li>
 *   <li>工具执行委托给 {@link McpToolClient}(而非本地 AgentTool 实现)</li>
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

    /** JSON 解析器(用于从 execute_sql 参数中提取 sql). */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String SYSTEM_PROMPT = """
            你是 NL2SQL 助手。你可以使用工具查询数据库,然后用中文回答用户的问题。
            工具使用规则:
            1. 必须先调用 get_schema 获取数据库表结构
            2. 再调用 execute_sql 执行只读 SQL
            3. 严格基于工具返回的实际数据作答,不要捏造表名/字段名/数值
            4. 当工具返回 Error 字符串时,必须修改 SQL 或策略后再次调用,直到成功或信息充分
            """;

    /** 硬编码的工具定义,与 MCP Server 注册的工具保持一致. */
    private static final List<ToolDefinition> TOOL_DEFS = List.of(
            new ToolDefinition(
                    "get_schema",
                    "获取数据库中所有表的结构(表名 + 字段名 + 字段类型 + 是否非空 + 字段注释)。"
                            + "必须作为首个工具调用,以便了解有哪些表可用。",
                    Map.of(
                            "type", "object",
                            "properties", Map.of(),
                            "required", List.of()
                    )
            ),
            new ToolDefinition(
                    "execute_sql",
                    "执行只读 SELECT SQL(自动注入 LIMIT 上限 100)。"
                            + "返回 JSON 格式的行数据与列信息。",
                    Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "sql", Map.of(
                                            "type", "string",
                                            "description", "要执行的 SQL 语句,必须为 SELECT,"
                                                    + "不能为 INSERT/UPDATE/DELETE/DROP"
                                    )
                            ),
                            "required", List.of("sql")
                    )
            )
    );

    private final LlmClient llmClient;
    private final McpToolClient mcpToolClient;

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
        // 1. 初始化消息列表
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("system", SYSTEM_PROMPT));
        messages.add(new ChatMessage("user", question));

        List<ToolCallRecord> toolCalls = new ArrayList<>();

        // 2. 循环
        for (int round = 1; round <= MAX_ROUNDS; round++) {
            LlmResponse resp = llmClient.chatWithTools(messages, TOOL_DEFS);

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
     * 通过 {@link McpToolClient} 执行工具调用.
     *
     * <p>三层防御:
     * <ul>
     *   <li>工具名不存在 → 返回 {@code Error: unknown tool} 字符串</li>
     *   <li>参数解析失败 → 返回 {@code Error: ...}</li>
     *   <li>McpToolClient 实现应自行吞掉异常、以 Error 字符串返回</li>
     * </ul>
     */
    private String executeTool(ToolCall call) {
        if (call == null || call.function() == null) {
            return "Error: [MalformedCall] tool call is malformed";
        }
        String name = call.function().name();
        String args = call.function().arguments();

        return switch (name) {
            case "get_schema" -> mcpToolClient.getSchema();
            case "execute_sql" -> executeSql(args);
            default -> "Error: [UnknownTool] unknown tool '" + name + "'";
        };
    }

    /**
     * 从 LLM 参数 JSON 中提取 sql 并委托 McpToolClient 执行.
     */
    private String executeSql(String argumentsJson) {
        String sql;
        try {
            Map<String, Object> args = MAPPER.readValue(
                    argumentsJson == null ? "{}" : argumentsJson,
                    new TypeReference<Map<String, Object>>() {});
            Object sqlObj = args.get("sql");
            if (sqlObj == null) {
                return "Error: [MissingArgument] missing required argument 'sql'";
            }
            sql = sqlObj.toString();
        } catch (Exception e) {
            return "Error: [InvalidArguments] failed to parse arguments JSON: " + e.getMessage();
        }
        return mcpToolClient.executeSql(sql);
    }
}
