package com.example.claudedemo.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;

/**
 * 统一响应封装.
 *
 * <p>遵循 CLAUDE.md 5.2 规范：响应格式为 {@code {code, message, data, traceId}}，
 * code = 0 表示成功，非 0 表示业务异常。
 *
 * @param <T> 数据类型
 * @author claude-code
 * @since 0.0.1
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Result<T>(int code, String message, T data, String traceId) implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 业务成功码. */
    public static final int CODE_SUCCESS = 0;

    /**
     * 成功响应（不含 traceId）.
     *
     * @param data 业务数据
     * @param <T>  数据类型
     * @return 统一响应
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(CODE_SUCCESS, "ok", data, null);
    }

    /**
     * 成功响应（含 traceId）.
     *
     * @param data    业务数据
     * @param traceId 链路追踪 ID
     * @param <T>     数据类型
     * @return 统一响应
     */
    public static <T> Result<T> success(T data, String traceId) {
        return new Result<>(CODE_SUCCESS, "ok", data, traceId);
    }

    /**
     * 业务错误响应.
     *
     * @param code    业务错误码
     * @param message 错误描述
     * @param <T>     数据类型
     * @return 统一响应
     */
    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null, null);
    }
}
