package com.example.claudedemo.agent.memory;

import com.example.claudedemo.llm.ChatMessage;
import com.example.claudedemo.llm.LlmClient;
import com.example.claudedemo.llm.LlmResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MemoryCompressor} 单元测试.
 *
 * @since 0.0.1
 */
class MemoryCompressorTest {

    private LlmClient llmClient;
    private ObjectMapper objectMapper;
    private MemoryCompressor compressor;

    @BeforeEach
    void setUp() {
        llmClient = mock(LlmClient.class);
        objectMapper = new ObjectMapper();
        compressor = new MemoryCompressor(llmClient, objectMapper);
    }

    // ==================== buildPrompt ====================

    @Test
    void should_build_prompt_with_empty_old_summary() {
        SummaryMemory old = SummaryMemory.empty();
        List<ConversationTurn> evicted = List.of(
                new ConversationTurn("q1", "a1"),
                new ConversationTurn("q2", "a2"));

        String prompt = compressor.buildPrompt(old, evicted);

        assertTrue(prompt.contains("## 旧摘要(可能为空)"));
        assertTrue(prompt.contains("(无)"));
        assertTrue(prompt.contains("## 旧关键事实(可能为空)"));
        assertTrue(prompt.contains("## 被淘汰的对话轮次"));
        assertTrue(prompt.contains("Q1: q1"));
        assertTrue(prompt.contains("A1: a1"));
        assertTrue(prompt.contains("Q2: q2"));
        assertTrue(prompt.contains("A2: a2"));
        assertTrue(prompt.contains("请输出新的 JSON。"));
    }

    @Test
    void should_build_prompt_with_old_summary_and_facts() {
        SummaryMemory old = new SummaryMemory("旧摘要文本", List.of("fact-1", "fact-2"), 2L);
        String prompt = compressor.buildPrompt(old, List.of(new ConversationTurn("q", "a")));

        assertTrue(prompt.contains("旧摘要文本"));
        assertTrue(prompt.contains("- fact-1"));
        assertTrue(prompt.contains("- fact-2"));
        assertTrue(prompt.contains("Q1: q"));
    }

    // ==================== parseResponse ====================

    @Test
    void should_parse_plain_json_response() {
        SummaryMemory old = SummaryMemory.empty();
        SummaryMemory result = compressor.parseResponse(
                "{\"summary\":\"新摘要\",\"keyFacts\":[\"f1\",\"f2\"]}", old);

        assertNotNull(result);
        assertEquals("新摘要", result.summary());
        assertEquals(List.of("f1", "f2"), result.keyFacts());
        assertEquals(1L, result.version(), "old 为空时新版本为 1");
    }

    @Test
    void should_parse_markdown_wrapped_json() {
        String content = "```json\n{\"summary\":\"x\",\"keyFacts\":[]}\n```";
        SummaryMemory result = compressor.parseResponse(content, SummaryMemory.empty());

        assertNotNull(result);
        assertEquals("x", result.summary());
        assertEquals(1L, result.version());
    }

    @Test
    void should_increment_version_when_old_present() {
        SummaryMemory old = new SummaryMemory("old", List.of("of"), 5L);
        SummaryMemory result = compressor.parseResponse(
                "{\"summary\":\"new\",\"keyFacts\":[]}", old);

        assertNotNull(result);
        assertEquals(6L, result.version());
    }

    @Test
    void should_trim_summary_whitespace() {
        SummaryMemory result = compressor.parseResponse(
                "{\"summary\":\"   有空格的摘要  \",\"keyFacts\":[]}", SummaryMemory.empty());
        assertNotNull(result);
        assertEquals("有空格的摘要", result.summary());
    }

    @Test
    void should_return_null_when_summary_missing() {
        assertNull(compressor.parseResponse("{\"keyFacts\":[]}", SummaryMemory.empty()));
    }

    @Test
    void should_return_null_when_summary_blank() {
        assertNull(compressor.parseResponse("{\"summary\":\"   \",\"keyFacts\":[]}", SummaryMemory.empty()));
    }

    @Test
    void should_return_null_when_content_empty() {
        assertNull(compressor.parseResponse("", SummaryMemory.empty()));
        assertNull(compressor.parseResponse(null, SummaryMemory.empty()));
    }

    @Test
    void should_return_null_when_no_json_in_content() {
        assertNull(compressor.parseResponse("not a json", SummaryMemory.empty()));
    }

    @Test
    void should_return_null_when_json_malformed() {
        assertNull(compressor.parseResponse("{\"summary\":\"x\",", SummaryMemory.empty()));
    }

    @Test
    void should_tolerate_missing_keyFacts_field() {
        SummaryMemory result = compressor.parseResponse(
                "{\"summary\":\"x\"}", SummaryMemory.empty());
        assertNotNull(result);
        assertEquals(List.of(), result.keyFacts());
    }

    @Test
    void should_filter_blank_facts() {
        SummaryMemory result = compressor.parseResponse(
                "{\"summary\":\"x\",\"keyFacts\":[\"f1\",\"  \",\"\",\"f2\"]}", SummaryMemory.empty());
        assertNotNull(result);
        assertEquals(List.of("f1", "f2"), result.keyFacts());
    }

    // ==================== compress() 端到端 ====================

    @Test
    void should_call_llm_and_return_new_summary() {
        when(llmClient.chat(anyList()))
                .thenReturn(new LlmResponse(
                        "{\"summary\":\"新\",\"keyFacts\":[\"a\"]}", "stop"));

        SummaryMemory old = new SummaryMemory("旧", List.of("of"), 3L);
        List<ConversationTurn> evicted = List.of(new ConversationTurn("q", "a"));

        SummaryMemory result = compressor.compress(old, evicted);
        assertNotNull(result);
        assertEquals("新", result.summary());
        assertEquals(List.of("a"), result.keyFacts());
        assertEquals(4L, result.version());

        // 验证 LLM 收到了 system + user
        org.mockito.ArgumentCaptor<List<ChatMessage>> captor =
                org.mockito.ArgumentCaptor.forClass(List.class);
        verify(llmClient).chat(captor.capture());
        List<ChatMessage> msgs = captor.getValue();
        assertEquals(2, msgs.size());
        assertEquals("system", msgs.get(0).role());
        assertEquals("user", msgs.get(1).role());
        assertTrue(msgs.get(1).content().contains("旧"));
        assertTrue(msgs.get(1).content().contains("- of"));
        assertTrue(msgs.get(1).content().contains("Q1: q"));
    }

    @Test
    void should_return_null_when_llm_throws() {
        when(llmClient.chat(anyList())).thenThrow(new RestClientException("net error"));

        SummaryMemory result = compressor.compress(
                SummaryMemory.empty(),
                List.of(new ConversationTurn("q", "a")));

        assertNull(result, "LLM 异常时应返回 null,不抛");
    }

    @Test
    void should_return_null_when_llm_response_malformed() {
        when(llmClient.chat(anyList())).thenReturn(new LlmResponse("garbage", "stop"));

        SummaryMemory result = compressor.compress(
                SummaryMemory.empty(),
                List.of(new ConversationTurn("q", "a")));

        assertNull(result);
    }

    @Test
    void should_return_old_when_nothing_to_compress() {
        // old 空 + evicted 空 → 直接返回 old,不调 LLM
        SummaryMemory old = SummaryMemory.empty();
        SummaryMemory result = compressor.compress(old, List.of());
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(llmClient, org.mockito.Mockito.never()).chat(anyList());
    }

    @Test
    void should_return_old_when_only_old_present_and_no_evicted() {
        SummaryMemory old = new SummaryMemory("已有摘要", List.of("of"), 2L);
        SummaryMemory result = compressor.compress(old, List.of());
        // 旧摘要非空,但 evicted 为空:仍跳过 LLM,返回原值
        assertNotNull(result);
        assertEquals("已有摘要", result.summary());
        verify(llmClient, org.mockito.Mockito.never()).chat(anyList());
    }

    // ==================== 鲁棒性:null/blank 输入 ====================

    @Test
    void should_handle_null_inputs() {
        SummaryMemory result = compressor.compress(null, null);
        assertNotNull(result);
        assertTrue(result.isEmpty());

        when(llmClient.chat(anyList())).thenReturn(new LlmResponse("{\"summary\":\"x\"}", "stop"));
        SummaryMemory result2 = compressor.compress(null, List.of(new ConversationTurn("q", "a")));
        assertNotNull(result2);
        assertFalse(result2.isEmpty());
        assertEquals(1L, result2.version(), "old 为 null 视为空,新 version=1");
    }
}
