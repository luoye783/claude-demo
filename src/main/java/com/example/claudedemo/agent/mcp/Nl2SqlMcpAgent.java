package com.example.claudedemo.agent.mcp;

import com.example.claudedemo.agent.ToolCallRecord;
import com.example.claudedemo.agent.ToolCallingExhaustedException;
import com.example.claudedemo.agent.ToolCallingResult;
import com.example.claudedemo.agent.memory.ConversationMemory;
import com.example.claudedemo.agent.memory.ConversationStore;
import com.example.claudedemo.agent.memory.ConversationTurn;
import com.example.claudedemo.agent.memory.SummaryMemory;
import com.example.claudedemo.agent.planner.AgentPlan;
import com.example.claudedemo.agent.planner.AgentPlanner;
import com.example.claudedemo.agent.planner.PlannerProperties;
import com.example.claudedemo.agent.rag.RagDocument;
import com.example.claudedemo.agent.rag.RagProperties;
import com.example.claudedemo.agent.rag.RagRetriever;
import com.example.claudedemo.agent.session.AgentSession;
import com.example.claudedemo.agent.trace.AgentTrace;
import com.example.claudedemo.agent.trace.StepType;
import com.example.claudedemo.llm.ChatMessage;
import com.example.claudedemo.llm.LlmClient;
import com.example.claudedemo.llm.LlmResponse;
import com.example.claudedemo.llm.ToolCall;
import com.example.claudedemo.llm.ToolDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
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
 *       {@link com.example.claudedemo.agent.memory.CompressionPolicy} 决定是否触发
 *       {@link com.example.claudedemo.agent.memory.MemoryCompressor} 生成
 *       {@link SummaryMemory} 并淘汰老 turn</li>
 *   <li>Agent 不感知压缩细节;messages 拼装时若 memory 携带 summary,自动插入
 *       一条 system 消息({@code ## 历史摘要 + ## 关键事实})作为 LLM 背景</li>
 *   <li>压缩 trace 步骤(若有)由 store 统一写入,Agent 不重复记录</li>
 * </ul>
 *
 * <p><b>V2 第五阶段:AgentSession</b>
 * <ul>
 *   <li>本类内部不再分别操作 {@code memory}、{@code trace}、{@code tokenUsage}、
 *       {@code metadata},而是把它们打包成 {@link AgentSession} 三段式工作:
 *       {@code openSession} → {@code executeOnSession} → {@code finalizeSession}</li>
 *   <li>公开方法签名、返回类型、异常类型、{@code ToolCallingResult} / {@code trace} 字段
 *       全部保持不变;解耦 Memory / Trace 子系统</li>
 * </ul>
 *
 * <p><b>V2 第六阶段:RAG V1</b>
 * <ul>
 *   <li>每次 {@code answer} 在拼装 messages 前调用 {@link RagRetriever#retrieve},把命中
 *       的业务知识文档作为一条 system 消息插入到 summary 之后、turns 之前</li>
 *   <li>通过 {@link ObjectProvider}{@code <RagRetriever>} 注入,未装配时降级为无 RAG 路径 —
 *       <b>不记 {@code RAG_RETRIEVE} trace 步骤</b>,保证老测试 trace 形状不变</li>
 *   <li>RAG 检索结果<b>不写入</b> {@link ConversationMemory}(会话历史 vs 外部知识职责分离)</li>
 *   <li>检索异常被吞掉,记 {@code RAG_RETRIEVE}(hits=0, note=error) 后继续主链路</li>
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
    /** RAG 检索器(V2 第六阶段,可选;null 时无 RAG 路径,不影响老测试). */
    private final RagRetriever ragRetriever;
    /** RAG 配置(V2 第六阶段,可空;默认全用 RagProperties 默认值). */
    private final RagProperties ragProps;
    /** Planner(V3 新增,可空;null 时跳过计划生成). */
    private final AgentPlanner planner;
    /** Planner 配置(V3 新增,始终非 null). */
    private final PlannerProperties plannerProps;

    /**
     * V1 兼容构造器:不注入 memoryStore,无记忆模式.
     */
    public Nl2SqlMcpAgent(LlmClient llmClient, McpToolClient mcpToolClient) {
        this(llmClient, mcpToolClient, null, null, null, null, null);
    }

    /**
     * V2 完整 Spring 构造器(对话记忆):支持 memoryStore;RAG 通过 {@link #configureRag} 注入.
     */
    @Autowired
    public Nl2SqlMcpAgent(LlmClient llmClient, McpToolClient mcpToolClient, ConversationStore memoryStore) {
        this(llmClient, mcpToolClient, memoryStore, null, null, null, null);
    }

    /**
     * 测试 / 手工注入用构造器:直接传 RagRetriever + RagProperties.
     */
    public Nl2SqlMcpAgent(LlmClient llmClient,
                          McpToolClient mcpToolClient,
                          ConversationStore memoryStore,
                          RagRetriever ragRetriever,
                          RagProperties ragProps) {
        this(llmClient, mcpToolClient, memoryStore, ragRetriever, ragProps, null, null);
    }

    /**
     * V3 完整构造器:含 Planner.
     *
     * @param llmClient     LLM 客户端
     * @param mcpToolClient MCP 工具客户端
     * @param memoryStore   会话记忆存储(可空)
     * @param ragRetriever  RAG 检索器(可空)
     * @param ragProps      RAG 配置(可空,默认用 RagProperties 默认值)
     * @param planner       Planner(可空,null 时跳过计划生成)
     * @param plannerProps  Planner 配置(可空,默认用关闭状态)
     */
    public Nl2SqlMcpAgent(LlmClient llmClient,
                          McpToolClient mcpToolClient,
                          ConversationStore memoryStore,
                          RagRetriever ragRetriever,
                          RagProperties ragProps,
                          AgentPlanner planner,
                          PlannerProperties plannerProps) {
        this.llmClient = llmClient;
        this.mcpToolClient = mcpToolClient;
        this.memoryStore = memoryStore;
        this.ragRetriever = ragRetriever;
        this.ragProps = (ragProps == null) ? new RagProperties() : ragProps;
        this.planner = planner;
        this.plannerProps = (plannerProps != null) ? plannerProps : new PlannerProperties();
        this.toolDefs = mcpToolClient.listTools();
        if (this.toolDefs.isEmpty()) {
            log.warn("Nl2SqlMcpAgent 构造期未从 MCP Server 拉取到任何工具,LLM 将无法调用工具");
        } else {
            log.info("Nl2SqlMcpAgent 已从 MCP Server 动态拉取 {} 个工具: {}",
                    this.toolDefs.size(),
                    this.toolDefs.stream().map(ToolDefinition::name).toList());
        }
        if (this.ragRetriever != null) {
            log.info("Nl2SqlMcpAgent 已启用 RAG:topK={}, minScore={}, maxContentChars={}",
                    this.ragProps.getTopK(), this.ragProps.getMinScore(), this.ragProps.getMaxContentChars());
        }
        if (this.planner != null) {
            log.info("Nl2SqlMcpAgent 已启用 Planner:enabled={}, type={}",
                    this.plannerProps.isEnabled(), this.plannerProps.getType());
        }
    }

    /**
     * Spring 可选注入 RAG 依赖(占位方法,保留用于未来扩展).
     *
     * <p>由于 {@link #ragRetriever} 与 {@link #ragProps} 是 final 字段,本类 V1 设计
     * 下 RAG 通过 5-arg 构造器直传(测试场景);Spring 默认装配的 3-arg 构造器
     * 把 RAG 留空,运行时走"无 RAG"路径。
     *
     * <p>如未来需要 Spring 自动注入 RAG,推荐做法:把这两个字段改为非 final,
     * 删去 5-arg 构造器,本方法改为:
     * <pre>{@code
     * @Autowired(required = false)
     * public void configureRag(ObjectProvider<RagRetriever> provider) {
     *     this.ragRetriever = provider.getIfAvailable();
     * }
     * }</pre>
     */
    public void configureRag() {
        // 占位:no-op
    }

    // ==================== 公开入口 ====================

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
        AgentSession session = openSession(null, question);
        generatePlan(session, question);
        List<ChatMessage> messages = buildInitialMessages(session, question);
        ToolLoopResult loop = executeOnSession(session, question, messages);
        return finalizeSession(session, question, loop);
    }

    /**
     * 回答一个自然语言问题(有记忆模式).
     *
     * <p>根据 {@code sessionId} 从 {@link ConversationStore} 加载历史,
     * 执行完 tool calling 循环后通过 {@link ConversationStore#appendTurn}
     * 写回本轮问答。是否触发压缩、是否更新摘要、是否淘汰 turn 全部由
     * {@link com.example.claudedemo.agent.memory.InMemoryConversationStore} 根据
     * {@link com.example.claudedemo.agent.memory.CompressionPolicy} 决定 —
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
        AgentSession session = openSession(sessionId, question);
        generatePlan(session, question);
        List<ChatMessage> messages = buildInitialMessages(session, question);
        ToolLoopResult loop = executeOnSession(session, question, messages);
        return finalizeSession(session, question, loop);
    }

    // ==================== Planner(V3 新增) ====================

    /**
     * 调用 Planner 生成执行计划,记录 trace + session metadata.
     *
     * <p>仅在 planner 非 null 且 enabled 时生效;不改变后续执行逻辑。
     */
    private void generatePlan(AgentSession session, String question) {
        if (planner != null && plannerProps.isEnabled()) {
            AgentPlan plan = planner.plan(session, question);
            session.trace().addStep(StepType.PLAN_CREATED, plan.summary());
            session.put("agentPlan", plan);
        }
    }

    // ==================== Session 三段式 ====================

    /**
     * 打开一次 session:建 trace、加载 memory、初始化 tokenUsage / metadata.
     *
     * <p>无记忆模式(sid 为 null)时,memory 为 null,后续 messages 拼装跳过 summary 与 turn。
     */
    private AgentSession openSession(String sid, String question) {
        ConversationMemory memory = (memoryStore != null && sid != null)
                ? memoryStore.getOrCreate(sid)
                : null;
        AgentTrace trace = new AgentTrace();
        trace.addStep(StepType.USER_QUESTION, question);
        return new AgentSession(sid, memory, trace);
    }

    /**
     * 根据 session 拼装初始 messages 列表:system + (可选)summary + (可选)RAG + recent turns + 当前问题.
     *
     * <p>RAG 段在 summary 之后、turns 之前(若两者同时存在);RAG 检索失败 / 命中 0 时不注入。
     */
    private List<ChatMessage> buildInitialMessages(AgentSession session, String question) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("system", SYSTEM_PROMPT));
        if (session.hasMemory()) {
            ConversationMemory memory = session.memory();
            if (memory.hasSummary()) {
                messages.add(new ChatMessage("system", buildSummaryMessage(memory)));
            }
        }
        // V2 第六阶段 RAG 注入(在 turns 之前,summary 之后)
        messages.addAll(buildRagContext(session, question));
        if (session.hasMemory()) {
            ConversationMemory memory = session.memory();
            if (!memory.isEmpty()) {
                messages.addAll(memory.toChatMessages());
            }
        }
        messages.add(new ChatMessage("user", question));
        return messages;
    }

    /**
     * 调用 {@link RagRetriever} 检索相关知识,渲染为 system 消息,并记 {@code RAG_RETRIEVE} trace 步骤.
     *
     * <p><b>行为约定</b>:
     * <ul>
     *   <li>{@code ragRetriever == null} → 返回空 list,<b>不记</b> trace 步骤(保持老 trace 形状)</li>
     *   <li>检索抛异常 → catch + log warn,记 {@code RAG_RETRIEVE}(hits=0, note=error),返回空 list</li>
     *   <li>命中 ≥1 → 截断到 {@code maxContentChars},渲染为单条 system 消息,记 {@code RAG_RETRIEVE}(hits=N)</li>
     *   <li>命中 0 → 不注入消息,记 {@code RAG_RETRIEVE}(hits=0)</li>
     * </ul>
     */
    private List<ChatMessage> buildRagContext(AgentSession session, String question) {
        if (ragRetriever == null) {
            return List.of();
        }
        long start = System.currentTimeMillis();
        int topK = ragProps.getTopK();
        List<RagDocument> docs;
        try {
            docs = ragRetriever.retrieve(question, topK);
        } catch (Exception e) {
            log.warn("RAG 检索失败,降级跳过: {}", e.getMessage());
            session.trace().addStep(StepType.RAG_RETRIEVE,
                    "query=\"" + truncateForTrace(question) + "\" topK=" + topK
                            + " hits=0 note=error",
                    System.currentTimeMillis() - start);
            return List.of();
        }
        // 应用 minScore 阈值
        double min = ragProps.getMinScore();
        List<RagDocument> filtered = docs.stream().filter(d -> d.score() >= min).toList();
        long duration = System.currentTimeMillis() - start;

        if (filtered.isEmpty()) {
            session.trace().addStep(StepType.RAG_RETRIEVE,
                    "query=\"" + truncateForTrace(question) + "\" topK=" + topK
                            + " hits=0",
                    duration);
            return List.of();
        }
        // 按 maxContentChars 截断(score 降序已由检索器保证)
        List<RagDocument> capped = capByContentChars(filtered, ragProps.getMaxContentChars());
        session.trace().addStep(StepType.RAG_RETRIEVE,
                "query=\"" + truncateForTrace(question) + "\" topK=" + topK
                        + " hits=" + capped.size(),
                duration);
        return List.of(new ChatMessage("system", formatRagDocuments(capped)));
    }

    /**
     * 将命中的 RAG 文档渲染为单条 system 消息.
     */
    private String formatRagDocuments(List<RagDocument> docs) {
        StringBuilder sb = new StringBuilder(256);
        sb.append("## 检索到的相关知识\n");
        for (RagDocument d : docs) {
            sb.append("\n### ").append(d.title()).append('\n');
            sb.append(d.content()).append('\n');
        }
        return sb.toString();
    }

    /**
     * 按总字符数上限截断文档列表(score 降序);保证至少返回 1 条。
     */
    private List<RagDocument> capByContentChars(List<RagDocument> docs, int maxChars) {
        if (maxChars <= 0) {
            return List.of();
        }
        List<RagDocument> out = new ArrayList<>();
        int total = 0;
        for (RagDocument d : docs) {
            int len = d.title().length() + d.content().length() + 8; // markdown 标题开销
            if (!out.isEmpty() && total + len > maxChars) {
                break;
            }
            out.add(d);
            total += len;
        }
        return out;
    }

    /**
     * 截断 question 用于 trace content(避免长 question 占满 trace).
     */
    private String truncateForTrace(String q) {
        if (q == null) return "";
        return q.length() <= 60 ? q : q.substring(0, 60) + "...";
    }

    /**
     * 在 session 上执行工具调用循环:从 {@code session.trace()} 记录步骤,
     * 未来 LLM 返回 usage 时往 {@code session.tokenUsage()} 累加。
     *
     * <p>成功时返回 {@link ToolLoopResult#success},失败(超最大轮数)返回
     * {@link ToolLoopResult#failure} — 由调用方决定如何收尾。
     */
    private ToolLoopResult executeOnSession(AgentSession session, String question,
                                            List<ChatMessage> messages) {
        AgentTrace trace = session.trace();
        List<ToolCallRecord> toolCalls = new ArrayList<>();

        for (int round = 1; round <= MAX_ROUNDS; round++) {
            trace.addStep(StepType.LLM_REQUEST, "round=" + round);

            long llmStart = System.currentTimeMillis();
            LlmResponse resp = llmClient.chatWithTools(messages, toolDefs);
            long llmDuration = System.currentTimeMillis() - llmStart;

            // TODO 未来: 解析 resp.usage() 累加到 session.tokenUsage()
            //  session.tokenUsage().addPrompt(resp.usage().promptTokens());
            //  session.tokenUsage().addCompletion(resp.usage().completionTokens());

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
     * 收尾:成功时通过 store 写回 turn(由 store 决定是否压缩),失败时直接抛异常.
     *
     * <p>返回 {@link ToolLoopResult#successResult};若 loop 失败,异常已由 store 之外抛回调用方。
     */
    private ToolCallingResult finalizeSession(AgentSession session, String question, ToolLoopResult loop) {
        if (loop.isSuccess()) {
            if (session.hasMemory() && memoryStore != null) {
                memoryStore.appendTurn(session.sessionId(),
                        new ConversationTurn(question, loop.answer()),
                        session.trace());
            }
            return loop.successResult;
        }
        throw loop.exhaustedException;
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
