package com.example.claudedemo.sql;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.alter.Alter;
import net.sf.jsqlparser.statement.create.table.CreateTable;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.drop.Drop;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.truncate.Truncate;
import net.sf.jsqlparser.statement.update.Update;
import org.springframework.stereotype.Component;

/**
 * SQL 校验器 V1.
 *
 * <p>仅支持 SELECT，自动注入 LIMIT 100，上限 1000。
 * 不处理 CTE / UNION / 子查询，由后续版本扩展。
 *
 * @author claude-code
 * @since 0.0.1
 */
@Component
public class SqlValidator {

    /** 默认注入的 LIMIT. */
    private static final int DEFAULT_LIMIT = 100;

    /** 允许的最大 LIMIT. */
    private static final int MAX_LIMIT = 1000;

    /**
     * 校验 SQL 并返回安全版本.
     *
     * @param rawSql 原始 SQL
     * @return 校验通过后的 SQL（已注入或保留 LIMIT）
     * @throws SqlValidationException 校验失败
     */
    public ValidatedSql validate(String rawSql) {
        // 1. 空值检查
        if (rawSql == null || rawSql.trim().isEmpty()) {
            throw new SqlValidationException(SqlErrorCode.SQL_EMPTY);
        }

        // 2. 解析 AST
        Statement stmt;
        try {
            stmt = CCJSqlParserUtil.parse(rawSql);
        } catch (JSQLParserException e) {
            throw new SqlValidationException(SqlErrorCode.SQL_PARSE_ERROR);
        }

        // 3. 必须是 SELECT
        if (!(stmt instanceof Select select)) {
            throw rejectNonSelect(stmt);
        }

        // 4. 处理 LIMIT：无则注入，有则校验上限
        boolean injected = false;
        long appliedLimit;
        Limit limit = select.getLimit();

        if (limit == null) {
            // JSqlParser 4.9 的 Limit 只有无参构造器，需用 withRowCount 链式 API
            select.setLimit(new Limit().withRowCount(new LongValue(DEFAULT_LIMIT)));
            appliedLimit = DEFAULT_LIMIT;
            injected = true;
        } else {
            long value = extractLimitValue(limit);
            if (value > MAX_LIMIT) {
                throw new SqlValidationException(SqlErrorCode.SQL_LIMIT_EXCEEDED);
            }
            appliedLimit = value;
        }

        return new ValidatedSql(select.toString(), (int) appliedLimit, injected);
    }

    /**
     * 提取 LIMIT 行数.
     */
    private long extractLimitValue(Limit limit) {
        Expression rowCount = limit.getRowCount();
        if (rowCount instanceof LongValue lv) {
            return lv.getValue();
        }
        try {
            return Long.parseLong(rowCount.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /**
     * 非 SELECT 语句的分类拒绝.
     */
    private SqlValidationException rejectNonSelect(Statement stmt) {
        if (stmt instanceof Insert || stmt instanceof Update || stmt instanceof Delete) {
            return new SqlValidationException(SqlErrorCode.SQL_DML_FORBIDDEN);
        }
        if (stmt instanceof Drop || stmt instanceof Alter || stmt instanceof Truncate
                || stmt instanceof CreateTable) {
            return new SqlValidationException(SqlErrorCode.SQL_DDL_FORBIDDEN);
        }
        return new SqlValidationException(SqlErrorCode.SQL_UNSUPPORTED);
    }
}
