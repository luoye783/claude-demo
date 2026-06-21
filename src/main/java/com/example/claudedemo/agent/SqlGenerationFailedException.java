package com.example.claudedemo.agent;

import java.util.Collections;
import java.util.List;

/**
 * SQL 自修复耗尽重试次数异常.
 *
 * <p>当 Nl2SqlAgent 在最大重试次数内仍无法生成可成功执行的 SQL 时抛出,
 * 通过 {@link #getSteps()} 拿到全部尝试记录,便于上层观测与排错。
 *
 * <p>异常 message 故意不包含 SQL 文本,仅含摘要,避免日志外泄。
 *
 * @author claude-code
 * @since 0.0.1
 */
public class SqlGenerationFailedException extends RuntimeException {

    private final List<AgentStep> steps;

    public SqlGenerationFailedException(int maxAttempts, List<AgentStep> steps) {
        super("SQL 自修复失败:已尝试 " + maxAttempts + " 轮,均未通过校验或执行");
        this.steps = steps == null ? List.of() : List.copyOf(steps);
    }

    /**
     * 全部尝试步骤(不可变副本).
     */
    public List<AgentStep> getSteps() {
        return Collections.unmodifiableList(steps);
    }
}
