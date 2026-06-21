package com.example.claudedemo.controller;

import com.example.claudedemo.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 健康检查接口.
 *
 * <p>提供 {@code GET /health} 用于存活探活与最小化验证。
 * 遵循 CLAUDE.md 5.1 规范：Controller 仅做参数返回，不写业务逻辑。
 *
 * @author claude-code
 * @since 0.0.1
 */
@RestController
public class HealthController {

    /**
     * 健康检查.
     *
     * @return 统一响应，包含服务状态、名称与时间戳
     */
    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        return Result.success(Map.of(
                "status", "UP",
                "service", "claude-demo",
                "timestamp", LocalDateTime.now().toString()
        ));
    }
}
