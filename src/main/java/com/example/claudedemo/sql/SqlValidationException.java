package com.example.claudedemo.sql;

import com.example.claudedemo.common.ErrorCode;

/**
 * SQL 校验异常.
 *
 * <p>V1 阶段临时继承 RuntimeException，阶段 6 升级为 BusinessException 子类。
 *
 * @author claude-code
 * @since 0.0.1
 */
public class SqlValidationException extends RuntimeException {

    private final ErrorCode errorCode;

    public SqlValidationException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /**
     * 错误码字符串（兼容旧 API，推荐改用 {@link #getErrorCode()}）.
     */
    public String getCode() {
        return errorCode.getCode();
    }

    /**
     * 错误码对象（类型安全，推荐用法）.
     */
    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
