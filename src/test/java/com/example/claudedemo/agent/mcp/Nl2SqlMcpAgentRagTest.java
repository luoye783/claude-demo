package com.example.claudedemo.agent.mcp;

import com.example.claudedemo.agent.ToolCallingResult;
import com.example.claudedemo.agent.memory.ConversationMemory;
import com.example.claudedemo.agent.memory.InMemoryConversationStore;
import com.example.claudedemo.agent.rag.InMemoryRagRetriever;
import com.example.claudedemo.agent.rag.RagProperties;
import com.example.claudedemo.agent.rag.RagRetriever;
import com.example.claudedemo.agent.trace.StepType;
import com.example.claudedemo.llm.ChatMessage;
import com.example.claudedemo.llm.LlmClient;
import com.example.claudedemo.llm.LlmResponse;
import com.example.claudedemo.llm.ToolDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link Nl2SqlMcpAgent} V2 第六阶段 RAG 集成测试.
 *
 * <p>覆盖:RAG context 注入、trace 步骤、RAG 不入 memory、命中 0 降级、检索异常降级、
 * 老行为兼容(ragRetriever=null 时不记 RAG_RETRIEVE)。
 *
 * @since 0.0.1
 */
class Nl2SqlMcpAgentRagTest {

    private static final List<ToolDefinition> FAKE_TOOL_DEFS = List.of(
            new ToolDefinition("get_schema",
                    "获取数据库所有表的结构",
                    Map.of("type", "object", "properties", Map.of(), "required", List.of())),
            new ToolDefinition("execute_sql",
                    "执行只读 SELECT SQL",
                    Map.of("type", "object", "properties", Map.of(
                            "sql", Map.of("type", "string")
                    ), "required", List.of("sql")))
    );

    private LlmClient llmClient;
    private McpToolClient mcpToolClient;
    private InMemoryConversationStore store;
    private RagRetriever ragRetriever;
    private RagProperties ragProps;

    @BeforeEach
    void setUp() {
        llmClient = mock(LlmClient.class);
        mcpToolClient = mock(McpToolClient.class);
        lenient().when(mcpToolClient.listTools()).thenReturn(FAKE_TOOL_DEFS);
        store = new InMemoryConversationStore();
        ragRetriever = new InMemoryRagRetriever();
        ragProps = new RagProperties();
    }

    // ==================== RAG 注入位置 ====================

    @Test
    void should_inject_rag_context_between_summary_and_turns() {
        when(llmClient.chatWithTools(anyList(), anyList()))
                .thenReturn(new LlmResponse("答", "stop", null));

        // 预置 memory 含 summary + 1 turn
        ConversationMemory memory = store.getOrCreate("sid-rag");
        memory.setSummary(new com.example.claudedemo.agent.memory.SummaryMemory(
                "S1", List.of("f1"), 1L));
        memory.addTurn(new com.example.claudedemo.agent.memory.ConversationTurn("旧Q", "旧A"));

        Nl2SqlMcpAgent agent = new Nl2SqlMcpAgent(llmClient, mcpToolClient, store, (RagRetriever) ragRetriever, ragProps);

        // 抓 messages 快照
        final List<List<ChatMessage>> captured = new ArrayList<>();
        doAnswer(inv -> {
            captured.add(new ArrayList<>(inv.getArgument(0)));
            return new LlmResponse("答", "stop", null);
        }).when(llmClient).chatWithTools(anyList(), anyList());

        agent.answer("sid-rag", "users 表怎么用?"); // "users" 关键词命中

        List<ChatMessage> msgs = captured.get(0);
        // 期望顺序: [system, system(摘要), system(RAG), user(旧), assistant(旧), user(新)]
        assertEquals(6, msgs.size());
        assertEquals("system", msgs.get(0).role());
        assertEquals("system", msgs.get(1).role());
        assertTrue(msgs.get(1).content().contains("## 历史摘要"), "索引 1 应为摘要");
        assertEquals("system", msgs.get(2).role());
        assertTrue(msgs.get(2).content().contains("## 检索到的相关知识"), "索引 2 应为 RAG");
        assertEquals("user", msgs.get(3).role());
        assertEquals("旧Q", msgs.get(3).content());
        assertEquals("assistant", msgs.get(4).role());
        assertEquals("user", msgs.get(5).role());
        assertEquals("users 表怎么用?", msgs.get(5).content());
    }

    // ==================== 无 RAG 模式 ====================

    @Test
    void should_not_inject_rag_when_retriever_null() {
        when(llmClient.chatWithTools(anyList(), anyList()))
                .thenReturn(new LlmResponse("答", "stop", null));

        // 显式传 null RagRetriever(模拟"无 RAG 模式")
        Nl2SqlMcpAgent agent = new Nl2SqlMcpAgent(llmClient, mcpToolClient, store, null, null);

        final List<List<ChatMessage>> captured = new ArrayList<>();
        doAnswer(inv -> {
            captured.add(new ArrayList<>(inv.getArgument(0)));
            return new LlmResponse("答", "stop", null);
        }).when(llmClient).chatWithTools(anyList(), anyList());

        agent.answer("sid", "users 表怎么用?");

        List<ChatMessage> msgs = captured.get(0);
        // 无 RAG 时,只能有 1 条 system(SYSTEM_PROMPT)
        long systemCount = msgs.stream().filter(m -> m.role().equals("system")).count();
        assertEquals(1, systemCount, "无 RAG 模式下不应有 RAG system 消息");
    }

    @Test
    void should_not_record_RAG_RETRIEVE_when_retriever_null() {
        when(llmClient.chatWithTools(anyList(), anyList()))
                .thenReturn(new LlmResponse("答", "stop", null));

        Nl2SqlMcpAgent agent = new Nl2SqlMcpAgent(llmClient, mcpToolClient, store, null, null);

        ToolCallingResult result = agent.answer("sid", "Q");

        // trace 中不应出现 RAG_RETRIEVE
        assertFalse(result.trace().steps().stream()
                .anyMatch(s -> s.stepType() == StepType.RAG_RETRIEVE),
                "ragRetriever=null 时不应记 RAG_RETRIEVE 步骤");
    }

    // ==================== 命中 0 降级 ====================

    @Test
    void should_not_inject_rag_when_no_match_but_record_trace() {
        when(llmClient.chatWithTools(anyList(), anyList()))
                .thenReturn(new LlmResponse("答", "stop", null));

        Nl2SqlMcpAgent agent = new Nl2SqlMcpAgent(llmClient, mcpToolClient, store, (RagRetriever) ragRetriever, ragProps);

        final List<List<ChatMessage>> captured = new ArrayList<>();
        doAnswer(inv -> {
            captured.add(new ArrayList<>(inv.getArgument(0)));
            return new LlmResponse("答", "stop", null);
        }).when(llmClient).chatWithTools(anyList(), anyList());

        ToolCallingResult result = agent.answer("sid", "completely unrelated content xyz");

        // 1) messages 无 RAG 段
        List<ChatMessage> msgs = captured.get(0);
        long systemCount = msgs.stream().filter(m -> m.role().equals("system")).count();
        assertEquals(1, systemCount, "命中 0 时不应注入 RAG 段");
        // 2) trace 仍记 RAG_RETRIEVE (hits=0)
        boolean hasRagStep = result.trace().steps().stream()
                .anyMatch(s -> s.stepType() == StepType.RAG_RETRIEVE);
        assertTrue(hasRagStep, "命中 0 时仍应记 RAG_RETRIEVE 步骤");
    }

    // ==================== 异常降级 ====================

    @Test
    void should_degrade_on_retriever_exception() {
        when(llmClient.chatWithTools(anyList(), anyList()))
                .thenReturn(new LlmResponse("答", "stop", null));

        RagRetriever failing = mock(RagRetriever.class);
        org.mockito.Mockito.doThrow(new RuntimeException("retriever down"))
                .when(failing).retrieve(anyString(), org.mockito.ArgumentMatchers.anyInt());

        Nl2SqlMcpAgent agent = new Nl2SqlMcpAgent(llmClient, mcpToolClient, store, failing, ragProps);

        final List<List<ChatMessage>> captured = new ArrayList<>();
        doAnswer(inv -> {
            captured.add(new ArrayList<>(inv.getArgument(0)));
            return new LlmResponse("答", "stop", null);
        }).when(llmClient).chatWithTools(anyList(), anyList());

        // 不应抛异常 — RAG 失败降级
        ToolCallingResult result = agent.answer("sid", "Q");

        // 1) messages 正常,无 RAG 段
        List<ChatMessage> msgs = captured.get(0);
        long systemCount = msgs.stream().filter(m -> m.role().equals("system")).count();
        assertEquals(1, systemCount, "RAG 异常降级时不应注入 RAG 段");
        // 2) trace 记 RAG_RETRIEVE 且含 note=error
        boolean hasErrorStep = result.trace().steps().stream()
                .anyMatch(s -> s.stepType() == StepType.RAG_RETRIEVE
                        && s.content() != null && s.content().contains("note=error"));
        assertTrue(hasErrorStep, "RAG 异常时应记 RAG_RETRIEVE(note=error)");
    }

    // ==================== RAG 不入 memory ====================

    @Test
    void should_not_write_rag_to_memory() {
        when(llmClient.chatWithTools(anyList(), anyList()))
                .thenReturn(new LlmResponse("最终答案", "stop", null));

        Nl2SqlMcpAgent agent = new Nl2SqlMcpAgent(llmClient, mcpToolClient, store, (RagRetriever) ragRetriever, ragProps);
        agent.answer("sid-rag-mem", "users 表怎么用?");

        ConversationMemory memory = store.find("sid-rag-mem").orElseThrow();
        assertEquals(1, memory.size());
        // turn.answer 不应包含 RAG 文档字样
        String answer = memory.turns().get(0).answer();
        assertEquals("最终答案", answer, "turn 只存最终答案,不应含 RAG 内容");
        assertFalse(answer.contains("## 检索到的相关知识"));
    }

    // ==================== trace 内容 ====================

    @Test
    void should_record_RAG_RETRIEVE_step_with_query_topK_hits_duration() {
        when(llmClient.chatWithTools(anyList(), anyList()))
                .thenReturn(new LlmResponse("答", "stop", null));

        Nl2SqlMcpAgent agent = new Nl2SqlMcpAgent(llmClient, mcpToolClient, store, (RagRetriever) ragRetriever, ragProps);

        ToolCallingResult result = agent.answer("sid", "users 表");

        // 找 RAG_RETRIEVE 步骤
        var ragStep = result.trace().steps().stream()
                .filter(s -> s.stepType() == StepType.RAG_RETRIEVE)
                .findFirst().orElseThrow();
        String content = ragStep.content();
        assertNotNull(content);
        assertTrue(content.contains("query="), "应含 query: " + content);
        assertTrue(content.contains("topK="), "应含 topK: " + content);
        assertTrue(content.contains("hits="), "应含 hits: " + content);
        // duration > 0
        assertTrue(ragStep.durationMs() >= 0L, "duration 应 >= 0");
    }

    // ==================== 字符上限 ====================

    @Test
    void should_cap_total_content_chars() {
        when(llmClient.chatWithTools(anyList(), anyList()))
                .thenReturn(new LlmResponse("答", "stop", null));

        // 设置很小的 maxContentChars 强制截断
        RagProperties tight = new RagProperties();
        tight.setMaxContentChars(50);

        Nl2SqlMcpAgent agent = new Nl2SqlMcpAgent(llmClient, mcpToolClient, store, (RagRetriever) ragRetriever, tight);

        final List<List<ChatMessage>> captured = new ArrayList<>();
        doAnswer(inv -> {
            captured.add(new ArrayList<>(inv.getArgument(0)));
            return new LlmResponse("答", "stop", null);
        }).when(llmClient).chatWithTools(anyList(), anyList());

        agent.answer("sid", "users city status");

        List<ChatMessage> msgs = captured.get(0);
        // 找到 RAG 段
        String ragContent = msgs.stream()
                .filter(m -> m.role().equals("system"))
                .map(ChatMessage::content)
                .filter(c -> c != null && c.contains("## 检索到的相关知识"))
                .findFirst().orElseThrow();
        // 总字符数 ≤ maxContentChars + "## 检索到的相关知识\n" 头开销
        assertTrue(ragContent.length() <= 50 + 30,
                "RAG 消息总长度应受 maxContentChars 约束, 实际: " + ragContent.length());
    }

    // ==================== 兼容性背书 ====================

    @Test
    void should_keep_old_constructor_signatures_working() {
        when(llmClient.chatWithTools(anyList(), anyList()))
                .thenReturn(new LlmResponse("答", "stop", null));

        // 双参构造器
        Nl2SqlMcpAgent a1 = new Nl2SqlMcpAgent(llmClient, mcpToolClient);
        // 三参构造器
        Nl2SqlMcpAgent a2 = new Nl2SqlMcpAgent(llmClient, mcpToolClient, store);

        assertNotNull(a1);
        assertNotNull(a2);

        ToolCallingResult r1 = a1.answer("Q");
        ToolCallingResult r2 = a2.answer("sid-c", "Q");

        // 都不应记 RAG_RETRIEVE
        assertFalse(r1.trace().steps().stream()
                .anyMatch(s -> s.stepType() == StepType.RAG_RETRIEVE));
        assertFalse(r2.trace().steps().stream()
                .anyMatch(s -> s.stepType() == StepType.RAG_RETRIEVE));
    }

    // ==================== 与 summary 共存 ====================

    @Test
    void should_inject_rag_after_summary_when_both_present() {
        when(llmClient.chatWithTools(anyList(), anyList()))
                .thenReturn(new LlmResponse("答", "stop", null));

        ConversationMemory memory = store.getOrCreate("sid-both");
        memory.setSummary(new com.example.claudedemo.agent.memory.SummaryMemory("S1", List.of(), 1L));

        // 用 "users 表" 多 token 命中,确保 score > min-score 默认 0.05
        // 单独用一个 min-score=0 的 properties 简化测试
        RagProperties loose = new RagProperties();
        loose.setMinScore(0.0);

        Nl2SqlMcpAgent agent = new Nl2SqlMcpAgent(llmClient, mcpToolClient, store,
                (RagRetriever) ragRetriever, loose);

        final List<List<ChatMessage>> captured = new ArrayList<>();
        doAnswer(inv -> {
            captured.add(new ArrayList<>(inv.getArgument(0)));
            return new LlmResponse("答", "stop", null);
        }).when(llmClient).chatWithTools(anyList(), anyList());

        agent.answer("sid-both", "users 表");

        List<ChatMessage> msgs = captured.get(0);
        // 系统消息顺序: [SYSTEM_PROMPT, summary, RAG]
        List<ChatMessage> systems = msgs.stream()
                .filter(m -> m.role().equals("system")).toList();
        assertEquals(3, systems.size());
        assertTrue(systems.get(0).content().contains("你是 NL2SQL 助手"));
        assertTrue(systems.get(1).content().contains("## 历史摘要"));
        assertTrue(systems.get(2).content().contains("## 检索到的相关知识"));
    }
}
