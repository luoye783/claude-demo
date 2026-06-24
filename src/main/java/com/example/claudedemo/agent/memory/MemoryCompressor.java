package com.example.claudedemo.agent.memory;

import com.example.claudedemo.llm.ChatMessage;
import com.example.claudedemo.llm.LlmClient;
import com.example.claudedemo.llm.LlmResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 会话记忆压缩器.
 *
 * <p>职责：基于"旧摘要 + 被淘汰 turn 列表"调用 LLM 生成新的 {@link SummaryMemory}。
 * 由 {@link InMemoryConversationStore#appendTurn} 在 turn 数超过阈值时调用。
 *
 * <p><b>失败策略</b>：任何异常(LLM 报错 / 解析失败 / 返回空)都返回 {@code null},
 * <b>不抛出异常</b>;调用方收到 null 时应保持原 summary 与 turns 不变,仅记录告警。
 *
 * <p><b>JSON 鲁棒解析</b>:
 * <ul>
 *   <li>从 LLM content 中用正则提取首个 {@code {...}} JSON 块(允许被 ```json 包裹)</li>
 *   <li>用 Jackson 解析;缺字段时该字段取 null/空</li>
 *   <li>若 summary 为空或缺字段 → 视为解析失败,返回 null</li>
 * </ul>
 *
 * <p><b>version 自增</b>:从 {@code old.version} 推算;old 为空时新摘要 version=1。
 *
 * @since 0.0.1
 */
@Component
public class MemoryCompressor {

    private static final Logger log = LoggerFactory.getLogger(MemoryCompressor.class);

    /** 提取首个 JSON 对象的正则(非贪婪,支持跨行). */
    private static final Pattern JSON_OBJECT = Pattern.compile("\\{[\\s\\S]*?\\}");

    private static final String SYSTEM_PROMPT = """
            你是一个会话压缩助手。给定"旧摘要"(可能为空)、"旧关键事实"(可能为空)
            与"若干被淘汰的对话轮次",请输出更新后的会话摘要与关键事实列表。

            要求:
            1. 摘要使用中文,简洁陈述,200 字以内,覆盖: 用户的核心问题、已得出的结论、当前的关注点。
            2. keyFacts 是必须长期记住的事实(实体名/字段名/数值/结论),3-8 条,每条一行;保留旧事实中仍有效的部分,补充新事实。
            3. 严格基于输入,不要捏造、不要补充未给出的信息。
            4. 只返回 JSON,不要解释、不要 markdown 之外的任何文字:
               {"summary": "...", "keyFacts": ["...", "..."]}
            """;

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public MemoryCompressor(LlmClient llmClient, ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 基于旧摘要与待淘汰 turn 生成新摘要.
     *
     * <p>失败时(LLM 异常/解析失败/字段缺失)返回 {@code null},不抛异常。
     *
     * @param old     旧摘要,可为 {@code null} 或 {@link SummaryMemory#empty()}
     * @param evicted 被淘汰的 turn 列表(可能为空)
     * @return 新摘要;失败为 null
     */
    public SummaryMemory compress(SummaryMemory old, List<ConversationTurn> evicted) {
        SummaryMemory oldSafe = (old == null) ? SummaryMemory.empty() : old;
        List<ConversationTurn> evictedSafe = (evicted == null) ? List.of() : evicted;

        // 输入无意义:无淘汰 turn → 旧摘要已是最新,无需重新生成
        if (evictedSafe.isEmpty()) {
            return oldSafe;
        }

        String prompt = buildPrompt(oldSafe, evictedSafe);
        try {
            LlmResponse resp = llmClient.chat(List.of(
                    new ChatMessage("system", SYSTEM_PROMPT),
                    new ChatMessage("user", prompt)
            ));
            if (resp == null) {
                log.warn("MemoryCompressor: LLM 返回 null 响应");
                return null;
            }
            return parseResponse(resp.content(), oldSafe);
        } catch (Exception e) {
            log.warn("MemoryCompressor: LLM 调用失败,降级保留旧摘要: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 构造压缩 prompt.
     *
     * <p>结构: 旧摘要 / 旧关键事实 / 被淘汰对话 → 要求输出新 JSON。
     */
    String buildPrompt(SummaryMemory old, List<ConversationTurn> evicted) {
        StringBuilder sb = new StringBuilder(512);
        sb.append("## 旧摘要(可能为空)\n");
        sb.append(old.isEmpty() || old.summary() == null || old.summary().isBlank()
                ? "(无)\n" : old.summary()).append('\n');
        sb.append("\n## 旧关键事实(可能为空)\n");
        if (old.keyFacts().isEmpty()) {
            sb.append("(无)\n");
        } else {
            for (String fact : old.keyFacts()) {
                sb.append("- ").append(fact).append('\n');
            }
        }
        sb.append("\n## 被淘汰的对话轮次\n");
        if (evicted.isEmpty()) {
            sb.append("(无)\n");
        } else {
            int idx = 1;
            for (ConversationTurn turn : evicted) {
                sb.append("Q").append(idx).append(": ").append(turn.question()).append('\n');
                sb.append("A").append(idx).append(": ").append(turn.answer()).append('\n');
                idx++;
            }
        }
        sb.append("\n请输出新的 JSON。");
        return sb.toString();
    }

    /**
     * 解析 LLM 响应,提取 summary + keyFacts,生成新 {@link SummaryMemory}.
     *
     * <p>任何环节失败都返回 null,绝不抛异常。
     */
    SummaryMemory parseResponse(String content, SummaryMemory old) {
        if (content == null || content.isBlank()) {
            log.warn("MemoryCompressor: LLM 响应内容为空");
            return null;
        }
        String json = extractFirstJsonObject(content);
        if (json == null) {
            log.warn("MemoryCompressor: 响应中未找到 JSON 对象, content={}", truncate(content));
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            String summary = node.path("summary").asText(null);
            if (summary == null || summary.isBlank()) {
                log.warn("MemoryCompressor: JSON 缺 summary 字段或为空");
                return null;
            }
            List<String> facts = parseKeyFacts(node.path("keyFacts"));
            long nextVersion = old.isEmpty() ? 1L : old.version() + 1;
            return new SummaryMemory(summary.trim(), facts, nextVersion);
        } catch (Exception e) {
            log.warn("MemoryCompressor: JSON 解析失败: {}, content={}", e.getMessage(), truncate(content));
            return null;
        }
    }

    private List<String> parseKeyFacts(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (JsonNode item : node) {
            String text = item.asText(null);
            if (text != null && !text.isBlank()) {
                out.add(text.trim());
            }
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * 从字符串中提取首个 JSON 对象(支持 ```json ... ``` 包裹).
     */
    private String extractFirstJsonObject(String content) {
        if (content == null) {
            return null;
        }
        // 去除 markdown 代码块包裹
        String stripped = content.trim();
        if (stripped.startsWith("```")) {
            int firstNewline = stripped.indexOf('\n');
            if (firstNewline > 0) {
                stripped = stripped.substring(firstNewline + 1);
            }
            if (stripped.endsWith("```")) {
                stripped = stripped.substring(0, stripped.length() - 3);
            }
        }
        Matcher m = JSON_OBJECT.matcher(stripped);
        return m.find() ? m.group() : null;
    }

    private static String truncate(String s) {
        if (s == null) return "(null)";
        return s.length() <= 200 ? s : s.substring(0, 200) + "...";
    }
}
