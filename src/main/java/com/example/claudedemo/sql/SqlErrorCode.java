package com.example.claudedemo.sql;

import com.example.claudedemo.common.ErrorCode;

/**
 * SQL 校验错误码.
 *
 * <p>所有 code 默认使用枚举名（{@code name()}）作为字符串值，保证唯一性。
 *
 * @author claude-code
 * @since 0.0.1
 */
public enum SqlErrorCode implements ErrorCode {

    /** SQL 为空. */
    SQL_EMPTY("SQL 不能为空"),

    /** SQL 语法错误. */
    SQL_PARSE_ERROR("SQL 语法错误"),

    /** 禁止 DML. */
    SQL_DML_FORBIDDEN("禁止 DML 操作（INSERT/UPDATE/DELETE）"),

    /** 禁止 DDL. */
    SQL_DDL_FORBIDDEN("禁止 DDL 操作（DROP/ALTER/TRUNCATE/CREATE）"),

    /** 不支持的 SQL 类型. */
    SQL_UNSUPPORTED("仅支持 SELECT 查询"),

    /** LIMIT 超限. */
    SQL_LIMIT_EXCEEDED("LIMIT 超过最大限制");

    private final String message;

    SqlErrorCode(String message) {
        this.message = message;
    }

    @Override
    public String getCode() {
        return name();
    }

    @Override
    public String getMessage() {
        return message;
    }
}
