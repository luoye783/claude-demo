package com.example.claudedemo.common;

import com.example.claudedemo.agent.ToolCallingExhaustedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理器.
 *
 * <p>遵循 CLAUDE.md §5.3 规范:业务异常通过 {@code @ExceptionHandler} 统一封装为
 * {@link Result} 格式返回,不裸抛 stacktrace;HTTP 状态码与业务 code 解耦.
 *
 * <p><b>错误码分配</b>:
 * <ul>
 *   <li>4001 — Bean Validation 校验失败(请求参数不合法)</li>
 *   <li>5001 — Tool Calling 循环耗尽(LLM 始终未给出最终答案)</li>
 *   <li>5000 — 未预期的 RuntimeException 兜底</li>
 * </ul>
 *
 * @author claude-code
 * @since 0.0.1
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Bean Validation 校验失败. */
    private static final int CODE_VALIDATION_ERROR = 4001;

    /** Tool Calling 循环耗尽. */
    private static final int CODE_TOOL_EXHAUSTED = 5001;

    /** 未预期的运行时异常. */
    private static final int CODE_INTERNAL_ERROR = 5000;

    /**
     * Bean Validation 失败(Controller 入参 {@code @Valid} 触发).
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("请求参数校验失败: {}", msg);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(CODE_VALIDATION_ERROR, msg));
    }

    /**
     * Tool Calling 循环耗尽 — 已执行 MAX_ROUNDS 轮 LLM 仍未给出最终答案.
     *
     * <p>返回 200,业务 code = 5001(message 含摘要,完整上下文在异常对象里).
     */
    @ExceptionHandler(ToolCallingExhaustedException.class)
    public ResponseEntity<Result<ToolCallingExhaustedException>> handleToolExhausted(
            ToolCallingExhaustedException ex) {
        log.error("Tool Calling 循环耗尽: {}", ex.getMessage());
        // 业务 code 透传给调用方,完整上下文留在异常对象里(供排查)
        return ResponseEntity.ok(Result.error(CODE_TOOL_EXHAUSTED, ex.getMessage()));
    }

    /**
     * 未预期的 RuntimeException 兜底 — 永不泄露 stacktrace.
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Result<Void>> handleUnexpected(RuntimeException ex) {
        log.error("未预期的运行时异常", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error(CODE_INTERNAL_ERROR, "服务内部错误"));
    }
}
