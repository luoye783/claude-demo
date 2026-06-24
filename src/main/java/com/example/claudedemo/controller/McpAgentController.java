package com.example.claudedemo.controller;

import com.example.claudedemo.agent.ToolCallingResult;
import com.example.claudedemo.agent.mcp.Nl2SqlMcpAgent;
import com.example.claudedemo.common.Result;
import com.example.claudedemo.dto.McpAnswerRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * MCP Agent REST 接口(V2 第二阶段).
 *
 * <p>暴露 {@code Nl2SqlMcpAgent} 为 HTTP 接口,完整透传 {@link ToolCallingResult}
 * (含 trace 步骤、对话历史、工具调用记录),便于浏览器 / curl / Postman 端到端验证
 * Agent + LLM + MCP Server 三方协作链路.
 *
 * <p><b>接口</b>:
 * <ul>
 *   <li>{@code POST /api/mcp/answer} — 提交自然语言问题,返回 Agent 完整执行结果</li>
 * </ul>
 *
 * <p><b>装配条件</b>:通过 {@code mcp.agent.enabled} 开关控制整个 MCP 链路 — 关闭后
 * {@link com.example.claudedemo.agent.mcp.McpAgentConfig} 不装配,本 Controller
 * 注入 {@link Nl2SqlMcpAgent} 失败,Spring 启动报错(用户应主动管理开关).
 *
 * <p><b>调用示例</b>:
 * <pre>{@code
 * curl -X POST http://localhost:8080/api/mcp/answer \
 *      -H 'Content-Type: application/json' \
 *      -d '{"question":"用户表有多少人?"}'
 * }</pre>
 *
 * @author claude-code
 * @since 0.0.1
 */
@RestController
@RequestMapping("/api/mcp")
public class McpAgentController {

    private static final Logger log = LoggerFactory.getLogger(McpAgentController.class);

    private final Nl2SqlMcpAgent agent;

    public McpAgentController(Nl2SqlMcpAgent agent) {
        this.agent = agent;
        log.info("McpAgentController 已装配:POST /api/mcp/answer");
    }

    /**
     * 提交自然语言问题,通过 MCP Agent 链路获取答案.
     *
     * @param request 含 {@code question} 的请求体(已通过 Bean Validation 校验)
     * @return 统一响应,data 为 {@link ToolCallingResult}(含 trace)
     */
    @PostMapping("/answer")
    public Result<ToolCallingResult> answer(@Valid @RequestBody McpAnswerRequest request) {
        log.info("MCP Agent 收到问题:{}", request.question());
        ToolCallingResult result = agent.answer(request.question());
        // 透传 traceId 便于调用方关联日志
        return Result.success(result, result.trace().traceId());
    }
}
