package com.example.claudedemo.agent;

import com.example.claudedemo.llm.ChatMessage;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Prompt 构造器：集中管理 NL2SQL Agent 用到的所有 prompt 模板.
 *
 * <p>V1 SQL 自修复包含三个 prompt：
 * <ul>
 *   <li>{@link #buildSqlPrompt} — 首轮生成 SQL</li>
 *   <li>{@link #buildSqlRepairPrompt} — 修复轮基于失败信息重新生成 SQL</li>
 *   <li>{@link #buildAnswerPrompt} — 基于 SQL 结果生成中文答案</li>
 * </ul>
 *
 * @author claude-code
 * @since 0.0.1
 */
@Component
public class PromptBuilder {

    private static final String SYSTEM_SQL = """
            你是 SQL 生成器。根据给定的数据库 Schema 和用户问题，生成一条 SELECT SQL。
            要求：
            1. 只返回 SQL，不要任何解释
            2. 不要用 markdown 代码块包裹
            3. 不要加任何前后缀文字
            """;

    private static final String SYSTEM_ANSWER = """
            你是数据分析助手。根据用户问题、SQL 和查询结果，用中文给出简洁准确的回答。
            不要重复 SQL，只回答用户的问题。
            """;

    /**
     * 构造第一轮 LLM 调用：生成 SQL 的 prompt.
     */
    public List<ChatMessage> buildSqlPrompt(String question, String schemaText) {
        String user = "Schema:\n" + schemaText + "\n\n问题：" + question;
        return List.of(
                new ChatMessage("system", SYSTEM_SQL),
                new ChatMessage("user", user)
        );
    }

    /**
     * 构造修复轮 LLM 调用：基于上一轮失败信息重新生成 SQL 的 prompt.
     *
     * <p>复用 {@link #SYSTEM_SQL} 约束(只返回 SQL,不要 markdown / 解释),user 段追加
     * 上一次失败的 SQL 与错误信息,让 LLM 在反馈中修正。
     *
     * @param question     用户原始问题
     * @param schemaText   Schema 文本(与首轮保持一致)
     * @param failedSql    上一轮 LLM 生成但失败的 SQL
     * @param errorMessage 上一轮的失败原因摘要(已脱敏、已截断)
     */
    public List<ChatMessage> buildSqlRepairPrompt(String question, String schemaText,
                                                  String failedSql, String errorMessage) {
        String user = "Schema:\n" + schemaText
                + "\n\n问题：" + question
                + "\n\n上一次生成的 SQL(已失败)：\n" + failedSql
                + "\n\n错误信息：\n" + errorMessage
                + "\n\n请基于以上反馈重新生成 SQL,只返回 SQL 本身,不要任何解释、不要 markdown 包裹。";
        return List.of(
                new ChatMessage("system", SYSTEM_SQL),
                new ChatMessage("user", user)
        );
    }

    /**
     * 构造第二轮 LLM 调用：基于 SQL 结果生成中文答案的 prompt.
     */
    public List<ChatMessage> buildAnswerPrompt(String question, String sql, List<Map<String, Object>> rows) {
        String user = "问题：" + question
                + "\n\nSQL：" + sql
                + "\n\n结果（" + rows.size() + " 行）：\n" + formatRows(rows);
        return List.of(
                new ChatMessage("system", SYSTEM_ANSWER),
                new ChatMessage("user", user)
        );
    }

    /**
     * 格式化行数据为可读文本.
     */
    private String formatRows(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) {
            return "（无数据）";
        }
        return rows.stream()
                .map(row -> row.entrySet().stream()
                        .map(e -> e.getKey() + "=" + e.getValue())
                        .collect(Collectors.joining(", ", "{", "}")))
                .collect(Collectors.joining("\n"));
    }
}
