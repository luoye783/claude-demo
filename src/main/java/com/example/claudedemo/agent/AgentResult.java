package com.example.claudedemo.agent;

import com.example.claudedemo.sql.ValidatedSql;

import java.util.List;
import java.util.Map;

/**
 * Agent 单次执行的完整结果.
 *
 * <p>保留中间产物(生成的 SQL、校验后的 SQL、行)便于调试、日志与观测。
 *
 * <p><b>V1 SQL 自修复</b>:新增 {@link #steps} 字段记录每一轮尝试;首轮原始 SQL 仍
 * 保留在 {@link #generatedSql},最终成功轮 LLM 返回的原始 SQL 通过
 * {@link #finalGeneratedSql} 暴露,首轮即成功时两者相等。
 *
 * @param question          用户原始问题
 * @param generatedSql      <b>首轮</b> LLM 返回的原始 SQL
 * @param finalGeneratedSql 最终<b>成功轮</b> LLM 返回的原始 SQL(可能与 {@code generatedSql} 相同)
 * @param validatedSql      最终成功轮校验后的 SQL(含 LIMIT 注入信息)
 * @param rows              最终成功轮 SQL 执行结果
 * @param answer            LLM 基于 SQL 结果生成的中文答案
 * @param steps             全部尝试步骤(包含失败轮与成功轮),按 round 升序
 * @author claude-code
 * @since 0.0.1
 */
public record AgentResult(
        String question,
        String generatedSql,
        String finalGeneratedSql,
        ValidatedSql validatedSql,
        List<Map<String, Object>> rows,
        String answer,
        List<AgentStep> steps
) {
}
