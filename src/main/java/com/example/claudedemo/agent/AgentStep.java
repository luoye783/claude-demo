package com.example.claudedemo.agent;

import com.example.claudedemo.sql.ValidatedSql;

/**
 * Agent 单轮尝试的产物.
 *
 * <p>用于 V1 SQL 自修复：每轮生成 SQL 后,无论成功失败,都记录一条 {@code AgentStep},
 * 全部回填到 {@link AgentResult#steps()}。失败时可读 {@link #errorMessage()} 排查,
 * 成功时 {@link #validatedSql()} 非空、{@link #errorMessage()} 为 {@code null}。
 *
 * @param round         轮次,从 1 开始
 * @param generatedSql  本轮 LLM 原始返回的 SQL
 * @param validatedSql  校验通过后的 SQL(校验失败时为 {@code null})
 * @param success       本轮是否成功(校验通过且执行通过)
 * @param errorMessage  失败原因摘要(成功时为 {@code null})
 * @author claude-code
 * @since 0.0.1
 */
public record AgentStep(
        int round,
        String generatedSql,
        ValidatedSql validatedSql,
        boolean success,
        String errorMessage
) {

    /**
     * 构造失败步骤(校验失败场景).
     */
    public static AgentStep failure(int round, String generatedSql, String errorMessage) {
        return new AgentStep(round, generatedSql, null, false, errorMessage);
    }

    /**
     * 构造失败步骤(执行失败场景,校验已通过).
     */
    public static AgentStep executionFailure(int round, String generatedSql,
                                             ValidatedSql validatedSql, String errorMessage) {
        return new AgentStep(round, generatedSql, validatedSql, false, errorMessage);
    }

    /**
     * 构造成功步骤.
     */
    public static AgentStep success(int round, String generatedSql, ValidatedSql validatedSql) {
        return new AgentStep(round, generatedSql, validatedSql, true, null);
    }
}
