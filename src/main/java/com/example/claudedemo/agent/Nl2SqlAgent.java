package com.example.claudedemo.agent;

import com.example.claudedemo.llm.ChatMessage;
import com.example.claudedemo.llm.LlmClient;
import com.example.claudedemo.sql.SqlExecutor;
import com.example.claudedemo.sql.SqlValidationException;
import com.example.claudedemo.sql.SqlValidator;
import com.example.claudedemo.sql.ValidatedSql;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * NL2SQL Agent V1 + ReAct SQL 自修复.
 *
 * <p>主流程：选 schema → 生成并执行 SQL(可重试)→ 生成中文答案。
 * SQL 生成段为自修复循环:首轮失败时把"问题 / Schema / 失败 SQL / 错误信息"打包
 * 重新发给 LLM,最多重试 {@link #MAX_RETRY} 次;任一轮成功立即停止;超过上限抛
 * {@link SqlGenerationFailedException}。
 *
 * <p>依赖说明：
 * <ul>
 *   <li>{@link SchemaSelector} 选 schema（V1 全量，V2 可基于 question 过滤）</li>
 *   <li>{@link PromptBuilder} 构造 prompt 模板(含修复轮)</li>
 *   <li>{@link LlmClient} 调用 LLM</li>
 *   <li>{@link SqlValidator} / {@link SqlExecutor} 执行 SQL 链路</li>
 * </ul>
 *
 * @author claude-code
 * @since 0.0.1
 */
@Component
public class Nl2SqlAgent {

    private static final Logger log = LoggerFactory.getLogger(Nl2SqlAgent.class);

    /** 最大重试次数(不含首轮);总尝试次数 = {@code MAX_RETRY + 1}. */
    private static final int MAX_RETRY = 3;

    /** 修复 prompt 中错误信息最大长度(超过则截断). */
    private static final int MAX_ERROR_MESSAGE_LENGTH = 3000;

    private final SchemaSelector schemaSelector;
    private final LlmClient llmClient;
    private final PromptBuilder promptBuilder;
    private final SqlValidator sqlValidator;
    private final SqlExecutor sqlExecutor;

    public Nl2SqlAgent(SchemaSelector schemaSelector,
                       LlmClient llmClient,
                       PromptBuilder promptBuilder,
                       SqlValidator sqlValidator,
                       SqlExecutor sqlExecutor) {
        this.schemaSelector = schemaSelector;
        this.llmClient = llmClient;
        this.promptBuilder = promptBuilder;
        this.sqlValidator = sqlValidator;
        this.sqlExecutor = sqlExecutor;
    }

    /**
     * 回答一个自然语言问题.
     *
     * @param question 用户问题
     * @return 包含完整中间产物与全部重试步骤的结果
     * @throws SqlGenerationFailedException 当 SQL 自修复超过最大重试次数仍失败
     */
    public AgentResult answer(String question) {
        // 1. 选 schema
        String schema = schemaSelector.select(question);

        // 2. 生成 + 校验 + 执行(可重试)
        List<AgentStep> steps = new ArrayList<>();
        SqlAttempt attempt = generateAndExecuteWithRetry(question, schema, steps);

        // 3. 生成中文答案
        List<ChatMessage> answerMessages = promptBuilder.buildAnswerPrompt(
                question, attempt.validatedSql().sql(), attempt.rows());
        String answer = llmClient.chat(answerMessages).content();

        // 4. 组装结果:generatedSql 始终是首轮 LLM 原始返回;finalGeneratedSql 取最终成功轮
        AgentStep lastSuccess = steps.get(steps.size() - 1);
        return new AgentResult(
                question,
                steps.get(0).generatedSql(),
                lastSuccess.generatedSql(),
                attempt.validatedSql(),
                attempt.rows(),
                answer,
                List.copyOf(steps)
        );
    }

    /**
     * 自修复循环:生成 SQL → 校验 → 执行,失败时携带错误信息重新调 LLM.
     *
     * <p>不变式:
     * <ul>
     *   <li>每次 LLM 返回的 SQL 都必经 {@link SqlValidator}</li>
     *   <li>校验通过后的 SQL 都必经 {@link SqlExecutor}</li>
     *   <li>任一轮成功立即返回,不再调 LLM</li>
     *   <li>超过 {@code MAX_RETRY + 1} 次仍未成功抛 {@link SqlGenerationFailedException}</li>
     * </ul>
     */
    private SqlAttempt generateAndExecuteWithRetry(String question, String schema, List<AgentStep> steps) {
        int maxAttempts = MAX_RETRY + 1;
        String lastFailedSql = null;
        String lastErrorMessage = null;

        for (int round = 1; round <= maxAttempts; round++) {
            // 1. 调 LLM 生成 SQL
            List<ChatMessage> messages = (round == 1)
                    ? promptBuilder.buildSqlPrompt(question, schema)
                    : promptBuilder.buildSqlRepairPrompt(question, schema, lastFailedSql, lastErrorMessage);
            String generatedSql = cleanSql(llmClient.chat(messages).content());

            // 2. 校验
            ValidatedSql validatedSql;
            try {
                validatedSql = sqlValidator.validate(generatedSql);
            } catch (SqlValidationException e) {
                String err = formatValidationError(e);
                steps.add(AgentStep.failure(round, generatedSql, err));
                lastFailedSql = generatedSql;
                lastErrorMessage = err;
                log.warn("SQL 自修复 round={} 校验失败: {}", round, err);
                continue;
            }

            // 3. 执行
            List<Map<String, Object>> rows;
            try {
                rows = sqlExecutor.execute(validatedSql);
            } catch (DataAccessException e) {
                String err = formatExecutionError(e);
                steps.add(AgentStep.executionFailure(round, generatedSql, validatedSql, err));
                lastFailedSql = generatedSql;
                lastErrorMessage = err;
                log.warn("SQL 自修复 round={} 执行失败: {}", round, err);
                continue;
            }

            // 4. 成功
            steps.add(AgentStep.success(round, generatedSql, validatedSql));
            return new SqlAttempt(validatedSql, rows);
        }

        // 5. 超过最大尝试次数
        log.error("SQL 自修复失败:已尝试 {} 轮,均未通过校验或执行", maxAttempts);
        throw new SqlGenerationFailedException(maxAttempts, steps);
    }

    /**
     * 格式化校验错误为简洁的 [CODE] message 形式.
     */
    private String formatValidationError(SqlValidationException e) {
        return "[" + e.getErrorCode().getCode() + "] " + e.getMessage();
    }

    /**
     * 格式化执行错误:含异常类名 + 截断后的 message.
     *
     * <p>不在 prompt 中回显完整堆栈,避免 token 浪费与潜在敏感信息泄露;
     * 完整堆栈由 SLF4J 日志记录。
     */
    private String formatExecutionError(DataAccessException e) {
        String msg = e.getMessage();
        if (msg == null || msg.isBlank()) {
            msg = e.getClass().getSimpleName();
        } else if (msg.length() > MAX_ERROR_MESSAGE_LENGTH) {
            msg = msg.substring(0, MAX_ERROR_MESSAGE_LENGTH) + "...";
        }
        return "[" + e.getClass().getSimpleName() + "] " + msg;
    }

    /**
     * 清理 LLM 返回的 SQL：去掉 markdown 代码块包裹（```sql ... ```）.
     */
    private String cleanSql(String raw) {
        String s = raw.trim();
        if (s.startsWith("```")) {
            s = s.replaceAll("^```\\w*\\n?", "").replaceAll("```$", "").trim();
        }
        return s;
    }

    /**
     * 一次成功尝试的产物(私有 record,仅在重试循环与主流程之间传递).
     */
    private record SqlAttempt(ValidatedSql validatedSql, List<Map<String, Object>> rows) {
    }
}
