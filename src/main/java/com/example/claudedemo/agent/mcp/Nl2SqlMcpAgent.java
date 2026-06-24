package com.example.claudedemo.agent.mcp;

import com.example.claudedemo.agent.ToolCallRecord;
import com.example.claudedemo.agent.ToolCallingExhaustedException;
import com.example.claudedemo.agent.ToolCallingResult;
import com.example.claudedemo.agent.memory.ConversationMemory;
import com.example.claudedemo.agent.memory.ConversationStore;
import com.example.claudedemo.agent.memory.ConversationTurn;
import com.example.claudedemo.agent.memory.SummaryMemory;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * NL2SQL MCP Agent V2 — 通过 {@link McpToolClient} 调用 MCP Server 工具,带短期会话记忆.
 *
 * <p>主流程与 {@link com.example.claudedemo.agent.Nl2SqlToolAgent Nl2SqlToolAgent} 一致：
 * 维护 {@code messages} 列表 → 循环调 LLM(带 tools) → 若 LLM 返回
 * {@code tool_calls} 则通过 McpToolClient 逐个执行并以 {@code role=tool} 追加结果 → 若
 * LLM 不再请求工具则将 content 作为最终答案返回。
 *
 * <p><b>V2 新增:短期会话记忆</b>
 * <ul>
 *   <li>新增 {@link #answer(String, String)} 方法,按 {@code sessionId} 关联历史</li>
 *   <li>历史以 {@link ConversationTurn} 为单位保存:只保留用户提问与最终答案,
 *       不含 tool_call / tool_result / schema / SQL 结果</li>
 *   <li>超过 {@value ConversationMemory#MAX_TURNS} 轮后自动 FIFO 裁剪最旧 turn</li>
 *   <li>异常分支(超过最大轮数)不写记忆,避免污染下次会话</li>
 *   <li>旧 {@link #answer(String)} 保持无记忆模式,独立代码路径,不污染 store</li>
 * </ul>
 *
 * <p><b>V2 第四阶段:摘要压缩</b>
 * <ul>
 *   <li>每次成功调用后通过 {@link ConversationStore#appendTurn} 写回,store 内部根据
 *       {@link CompressionPolicy} 决定是否触发 {@link MemoryCompressor} 生成
 *       {@link SummaryMemory} 并淘汰老 turn</li>
 *   <li>Agent 不感知压缩细节;messages 拼装时若 memory 携带 summary,自动插入
 *       一条 system 消息({@code ## 历史摘要 + ## 关键事实})作为 LLM 背景</li>
 *   <li>压缩 trace 步骤(若有)由 store 统一写入,Agent 不重复记录</li>
 * </ul>
 *
 * <p><b>与 Nl2SqlToolAgent 的关键差异</b>：
 * <ul>
 *   <li>工具定义通过 {@link McpToolClient#listTools()} 在构造期动态拉取,而非硬编码或本地 AgentTool</li>
 *   <li>工具执行通过 {@link McpToolClient#callTool(String, String)} 通用转发,无 switch/case 枚举</li>
 *   <li>V2 起作为 Spring Bean 装配(由 {@link McpAgentConfig} 提供 {@link McpToolClient} 依赖);
 *       纯单测场景仍可直接 {@code new Nl2SqlMcpAgent(llmClient, mcpToolClient)}</li>
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
 * @since 0.0.1
 */
@Component
public class Nl2SqlMcpAgent {

    private static final Logger log = LoggerFactory.getLogger(Nl2SqlMcpAgent.class);

    /** 最大循环轮数(每轮 = 1 次 LLM 调用). */
    public static final int MAX_ROUNDS = 5;

    /** trace 中工具结果展示的最大字符数. */
    private static final int TOOL_RESULT_TRACE_LIMIT = 1000;

    private static final String SYSTEM_PROMPT = """
            你是 NL2SQL 助手。你可以使用工具查询数据库,然后用中文回答用户的问题。
            工具使用规则:
            1. 优先调用 schema 类工具了解数据库表结构
            2. 再用 SQL/查询类工具获取实际数据
            3. 严格基于工具返回的实际数据作答,不要捏造表名/字段名/数值
            4. 当工具返回 Error 字符串时,必须修改策略后再次调用,直到成功或信息充分
            """;

    private final LlmClient llmClient;
    private final McpToolClient mcpToolClient;
    /** 工具定义(构造期一次性从 MCP Server 拉取,后续循环复用). */
    private final List<ToolDefinition> toolDefs;
    /** 短期会话记忆存储(V2 新增,无记忆模式下为 null). */
    private final ConversationStore memoryStore;

    /**
     * V1 兼容构造器:不注入 memoryStore,无记忆模式.
     */
    public Nl2SqlMcpAgent(LlmClient llmClient, McpToolClient mcpToolClient) {
        this(llmClient, mcpToolClient, null);
    }

    /**
     * V2 完整构造器:支持会话记忆.
     *
     * @param llmClient   LLM 客户端
     * @param mcpToolClient MCP 工具客户端
     * @param memoryStore  会话记忆存储(传入 null 则 agent 工作于无记忆模式)
     */
    @Autowired
    public Nl2SqlMcpAgent(LlmClient llmClient, McpToolClient mcpToolClient, ConversationStore memoryStore) {
        this.llmClient = llmClient;
        this.mcpToolClient = mcpToolClient;
        this.memoryStore = memoryStore;
        this.toolDefs = mcpToolClient.listTools();
        if (this.toolDefs.isEmpty()) {
            log.warn("Nl2SqlMcpAgent 构造期未从 MCP Server 拉取到任何工具,LLM 将无法调用工具");
        } else {
            log.info("Nl2SqlMcpAgent 已从 MCP Server 动态拉取 {} 个工具: {}",
                    this.toolDefs.size(),
                    this.toolDefs.stream().map(ToolDefinition::name).toList());
        }
    }

    // ==================== 无记忆模式(独立路径) ====================

    /**
     * 回答一个自然语言问题(无记忆模式).
     *
     * <p>每次调用独立,不保存历史,不污染 {@link ConversationStore}.
     * 与 V1 行为完全一致,拥有独立的完整实现,不依赖任何记忆相关逻辑。
     *
     * @param question 用户问题
     * @return 包含完整对话历史、工具调用记录、最终答案与执行 trace 的结果
     * @throws ToolCallingExhaustedException 当超过 {@value #MAX_ROUNDS} 轮 LLM 仍未给出最终答案
     */
    public ToolCallingResult answer(String question) {
        AgentTrace trace = new AgentTrace();
        trace.addStep(StepType.USER_QUESTION, question);

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("system", SYSTEM_PROMPT));
        messages.add(new ChatMessage("user", question));

        List<ToolCallRecord> toolCalls = new ArrayList<>();

        for (int round = 1; round <= MAX_ROUNDS; round++) {
            trace.addStep(StepType.LLM_REQUEST, "round=" + round);

            long llmStart = System.currentTimeMillis();
            LlmResponse resp = llmClient.chatWithTools(messages, toolDefs);
            long llmDuration = System.currentTimeMillis() - llmStart;

            messages.add(new ChatMessage("assistant", resp.content(), resp.toolCalls(), null));

            String respContent = (resp.content() == null || resp.content().isEmpty())
                    ? "(tool call)"
                    : resp.content();
            trace.addStep(StepType.LLM_RESPONSE, respContent, llmDuration);

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

        log.error("MCP Agent 失败:已执行 {} 轮,LLM 始终未给出最终答案", MAX_ROUNDS);
        trace.addError("超过最大轮数 " + MAX_ROUNDS + ",LLM 始终未给出最终答案");
        throw new ToolCallingExhaustedException(
                MAX_ROUNDS,
                List.copyOf(messages),
                ToolCallRecord.copyOf(toolCalls),
                trace
        );
    }

    // ==================== 有记忆模式 ====================

    /**
     * 回答一个自然语言问题(有记忆模式).
     *
     * <p>根据 {@code sessionId} 从 {@link ConversationStore} 加载历史,
     * 执行完 tool calling 循环后通过 {@link ConversationStore#appendTurn}
     * 写回本轮问答。是否触发压缩、是否更新摘要、是否淘汰 turn 全部由
     * {@link InMemoryConversationStore} 根据 {@link CompressionPolicy} 决定 —
     * Agent 不感知这些细节。
     *
     * <p>异常分支(超过最大轮数)不写记忆,避免污染下次会话。
     *
     * @param sessionId 会话 ID,不可为空
     * @param question  用户问题
     * @return 包含完整对话历史、工具调用记录、最终答案与执行 trace 的结果
     * @throws IllegalArgumentException     当 sessionId 为空
     * @throws ToolCallingExhaustedException 当超过 {@value #MAX_ROUNDS} 轮 LLM 仍未给出最终答案
     */
    public ToolCallingResult answer(String sessionId, String question) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }

        AgentTrace trace = new AgentTrace();
        trace.addStep(StepType.USER_QUESTION, question);

        // 加载历史记忆
        ConversationMemory memory = (memoryStore != null) ? memoryStore.getOrCreate(sessionId) : null;

        // 拼装 messages: system + (可选)summary + recent turns + 当前问题
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("system", SYSTEM_PROMPT));
        if (memory != null) {
            // 摘要消息(若有):作为第二条 system 消息插入,LLM 视为历史背景
            if (memory.hasSummary()) {
                messages.add(new ChatMessage("system", buildSummaryMessage(memory)));
            }
            if (!memory.isEmpty()) {
                messages.addAll(memory.toChatMessages());
            }
        }
        messages.add(new ChatMessage("user", question));

        // 执行工具调用循环
        ToolLoopResult loopResult = executeToolLoop(question, messages, trace);

        // 成功时通过 store.appendTurn 写回本轮问答;压缩由 store 内部完成
        if (memory != null && loopResult.isSuccess()) {
            memoryStore.appendTurn(sessionId,
                    new ConversationTurn(question, loopResult.answer()),
                    trace);
        }

        if (loopResult.isSuccess()) {
            return loopResult.successResult;
        } else {
            throw loopResult.exhaustedException;
        }
    }

    /**
     * 将 {@link SummaryMemory} 渲染为 LLM 可消费的第二条 system 消息.
     *
     * <p>格式: 历史摘要段 + 关键事实列表,使用 markdown 标题便于 LLM 解析。
     */
    private String buildSummaryMessage(ConversationMemory memory) {
        SummaryMemory s = memory.summary();
        StringBuilder sb = new StringBuilder(256);
        sb.append("## 历史摘要\n");
        sb.append(s.summary() == null ? "(空)" : s.summary());
        if (s.keyFacts() != null && !s.keyFacts().isEmpty()) {
            sb.append("\n\n## 关键事实\n");
            for (String fact : s.keyFacts()) {
                sb.append("- ").append(fact).append('\n');
            }
        }
        return sb.toString();
    }

    // ==================== 工具调用循环(私有,被两个 answer 方法复用) ====================

    /**
     * 纯工具调用循环,不关心记忆。
     *
     * <p>接收已组装好的 {@code messages}(不含 memory 管理逻辑),执行 LLM 调用 + 工具执行循环。
     * 成功时返回 {@link ToolLoopResult#success(ToolCallingResult)},失败时(超最大轮数)返回
     * {@link ToolLoopResult#failure(ToolCallingExhaustedException)}。
     *
     * <p>本方法只负责执行,不做任何异常抛出/记忆写回 — 由调用方处理。
     */
    private ToolLoopResult executeToolLoop(String question, List<ChatMessage> messages, AgentTrace trace) {
        List<ToolCallRecord> toolCalls = new ArrayList<>();

        for (int round = 1; round <= MAX_ROUNDS; round++) {
            trace.addStep(StepType.LLM_REQUEST, "round=" + round);

            long llmStart = System.currentTimeMillis();
            LlmResponse resp = llmClient.chatWithTools(messages, toolDefs);
            long llmDuration = System.currentTimeMillis() - llmStart;

            messages.add(new ChatMessage("assistant", resp.content(), resp.toolCalls(), null));

            String respContent = (resp.content() == null || resp.content().isEmpty())
                    ? "(tool call)"
                    : resp.content();
            trace.addStep(StepType.LLM_RESPONSE, respContent, llmDuration);

            List<ToolCall> calls = resp.toolCalls();
            if (calls == null || calls.isEmpty()) {
                String finalAnswer = resp.content();
                log.info("MCP Agent round={} 给出最终答案:{} 字符", round,
                        finalAnswer == null ? 0 : finalAnswer.length());
                trace.addStep(StepType.FINAL_ANSWER, finalAnswer);

                return ToolLoopResult.success(new ToolCallingResult(
                        question,
                        finalAnswer,
                        List.copyOf(messages),
                        ToolCallRecord.copyOf(toolCalls),
                        round,
                        trace
                ));
            }

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

        log.error("MCP Agent 失败:已执行 {} 轮,LLM 始终未给出最终答案", MAX_ROUNDS);
        trace.addError("超过最大轮数 " + MAX_ROUNDS + ",LLM 始终未给出最终答案");
        return ToolLoopResult.failure(new ToolCallingExhaustedException(
                MAX_ROUNDS,
                List.copyOf(messages),
                ToolCallRecord.copyOf(toolCalls),
                trace
        ));
    }

    /**
     * 工具调用循环的结果包装.
     *
     * <p>用于在没有异常机制或需要异常前处理逻辑的场景下返回成功/失败.
     */
    private static final class ToolLoopResult {
        final ToolCallingResult successResult;
        final ToolCallingExhaustedException exhaustedException;

        private ToolLoopResult(ToolCallingResult successResult, ToolCallingExhaustedException exhaustedException) {
            this.successResult = successResult;
            this.exhaustedException = exhaustedException;
        }

        static ToolLoopResult success(ToolCallingResult r) {
            return new ToolLoopResult(r, null);
        }

        static ToolLoopResult failure(ToolCallingExhaustedException e) {
            return new ToolLoopResult(null, e);
        }

        boolean isSuccess() {
            return successResult != null;
        }

        String answer() {
            return successResult != null ? successResult.answer() : null;
        }
    }

    // ==================== 工具方法 ====================

    private String executeTool(ToolCall call) {
        if (call == null || call.function() == null) {
            return "Error: [MalformedCall] tool call is malformed";
        }
        String args = call.function().arguments();
        return mcpToolClient.callTool(call.function().name(), args == null ? "{}" : args);
    }

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
