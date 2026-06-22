package com.example.claudedemo.agent.tools;

import com.example.claudedemo.llm.ToolDefinition;
import com.example.claudedemo.sql.SqlExecutor;
import com.example.claudedemo.sql.SqlValidationException;
import com.example.claudedemo.sql.SqlValidator;
import com.example.claudedemo.sql.ValidatedSql;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * execute_sql 工具:执行只读 SQL.
 *
 * <p><b>安全边界(关键不变量)</b>:所有 SQL 必须经过 {@link SqlValidator#validate} 校验;
 * 即便 LLM 送来 {@code DROP / DELETE / UPDATE} 等危险语句,也会被校验器拒绝,
 * 工具返回错误字符串而不会影响数据库。
 *
 * <p><b>错误处理</b>:任何校验失败、执行异常、参数解析错误均被捕获并转为
 * {@code "Error: [...] ..."} 字符串返回,绝不上抛 — 保证 Agent 循环不被打断,
 * LLM 可基于错误信息自我修复(下次调用换一条 SQL)。
 *
 * @author claude-code
 * @since 0.0.1
 */
@Component
public class ExecuteSqlTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(ExecuteSqlTool.class);

    /** 工具名(V1 业务内唯一,作为 Agent 调度键). */
    public static final String NAME = "execute_sql";

    /** 错误信息最大长度(超过则截断,避免 token 浪费与敏感信息泄露). */
    private static final int MAX_ERROR_MESSAGE_LENGTH = 3000;

    /** 工具自带 ObjectMapper(行数据 JSON 序列化);避免依赖 Spring 上下文,方便单测. */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SqlValidator sqlValidator;
    private final SqlExecutor sqlExecutor;

    public ExecuteSqlTool(SqlValidator sqlValidator, SqlExecutor sqlExecutor) {
        this.sqlValidator = sqlValidator;
        this.sqlExecutor = sqlExecutor;
    }

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(
                NAME,
                "执行只读 SELECT SQL(自动注入 LIMIT 上限 100)。"
                        + "返回 JSON 格式的行数据与列信息。",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "sql", Map.of(
                                        "type", "string",
                                        "description", "要执行的 SQL 语句,必须为 SELECT,不能为 INSERT/UPDATE/DELETE/DROP"
                                )
                        ),
                        "required", List.of("sql")
                )
        );
    }

    @Override
    public String execute(String argumentsJson) {
        // 1. 解析参数
        String sql;
        try {
            Map<String, Object> args = MAPPER.readValue(argumentsJson, new TypeReference<>() {
            });
            Object sqlObj = args.get("sql");
            if (sqlObj == null) {
                return "Error: [MissingArgument] missing required argument 'sql'";
            }
            sql = sqlObj.toString();
        } catch (Exception e) {
            return "Error: [InvalidArguments] failed to parse arguments JSON: " + e.getMessage();
        }

        // 2. 校验(必经,不可绕过)
        ValidatedSql validated;
        try {
            validated = sqlValidator.validate(sql);
        } catch (SqlValidationException e) {
            return "Error: [" + e.getErrorCode().getCode() + "] " + e.getMessage();
        }

        // 3. 执行
        List<Map<String, Object>> rows;
        try {
            rows = sqlExecutor.execute(validated);
        } catch (DataAccessException e) {
            return "Error: [" + e.getClass().getSimpleName() + "] " + truncate(e.getMessage());
        } catch (Exception e) {
            // 兜底:不预期异常(如 NPE)也不能打断 Agent 循环
            log.warn("execute_sql 未预期异常: {}", e.toString());
            return "Error: [" + e.getClass().getSimpleName() + "] " + truncate(e.getMessage());
        }

        // 4. 序列化结果为 JSON
        try {
            return MAPPER.writeValueAsString(Map.of(
                    "sql", validated.sql(),
                    "rowCount", rows.size(),
                    "rows", rows
            ));
        } catch (Exception e) {
            return "Error: [SerializationFailed] failed to serialize rows: " + e.getMessage();
        }
    }

    /**
     * 截断过长的错误信息.
     */
    private String truncate(String msg) {
        if (msg == null || msg.isBlank()) {
            return "no message";
        }
        if (msg.length() > MAX_ERROR_MESSAGE_LENGTH) {
            return msg.substring(0, MAX_ERROR_MESSAGE_LENGTH) + "...";
        }
        return msg;
    }
}
