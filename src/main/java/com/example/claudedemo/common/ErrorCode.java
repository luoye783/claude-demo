package com.example.claudedemo.common;

/**
 * 错误码接口.
 *
 * <p>所有业务错误码枚举都应实现此接口，保证类型安全与统一管理。
 * 当前实现：{@code com.example.claudedemo.sql.SqlErrorCode}。
 *
 * @author claude-code
 * @since 0.0.1
 */
public interface ErrorCode {

    /**
     * 错误码字符串（外部传输、持久化、日志用）.
     *
     * <p>推荐用枚举名作为 code，例如 {@code "SQL_EMPTY"}。
     */
    String getCode();

    /**
     * 默认错误描述（用户可读的友好提示）.
     */
    String getMessage();
}
