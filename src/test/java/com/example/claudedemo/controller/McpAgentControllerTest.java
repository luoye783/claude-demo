package com.example.claudedemo.controller;

import com.example.claudedemo.agent.ToolCallingExhaustedException;
import com.example.claudedemo.agent.ToolCallingResult;
import com.example.claudedemo.agent.mcp.Nl2SqlMcpAgent;
import com.example.claudedemo.agent.trace.AgentTrace;
import com.example.claudedemo.agent.trace.StepType;
import com.example.claudedemo.agent.trace.TraceStep;
import com.example.claudedemo.common.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link McpAgentController} HTTP 入口测试.
 *
 * <p>策略:用 {@code @WebMvcTest(McpAgentController.class)} 只加载 Controller 与
 * {@link com.example.claudedemo.common.GlobalExceptionHandler} 等 Web 层组件,
 * {@link Nl2SqlMcpAgent} 通过 {@code @MockBean} 注入 — 这样:
 * <ul>
 *   <li>不会装配 {@code McpAgentConfig},避免 fork MCP server 子进程</li>
 *   <li>不依赖 LlmClient / 数据库 / 真实 MCP 通信,跑得快</li>
 *   <li>只验证 HTTP 路由、Bean Validation、异常处理路径</li>
 * </ul>
 *
 * @author claude-code
 * @since 0.0.1
 */
@WebMvcTest(McpAgentController.class)
class McpAgentControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private Nl2SqlMcpAgent agent;

    @Test
    void should_return_result_with_traceId_on_happy_path() throws Exception {
        // 构造一个含 trace 的 ToolCallingResult
        AgentTrace trace = new AgentTrace();
        trace.addStep(StepType.USER_QUESTION, "用户表有多少人?");
        trace.addStep(StepType.FINAL_ANSWER, "共有 3 个用户。");
        ToolCallingResult mockResult = new ToolCallingResult(
                "用户表有多少人?", "共有 3 个用户。", List.of(), List.of(), 1, trace);
        when(agent.answer(anyString())).thenReturn(mockResult);

        Map<String, String> body = Map.of("question", "用户表有多少人?");
        mockMvc.perform(post("/api/mcp/answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("ok"))
                // traceId 透传
                .andExpect(jsonPath("$.traceId").exists())
                // data 中关键字段
                .andExpect(jsonPath("$.data.question").value("用户表有多少人?"))
                .andExpect(jsonPath("$.data.answer").value("共有 3 个用户。"))
                .andExpect(jsonPath("$.data.rounds").value(1))
                .andExpect(jsonPath("$.data.trace.traceId").exists());
    }

    @Test
    void should_return_400_when_question_is_blank() throws Exception {
        Map<String, String> body = Map.of("question", "");
        mockMvc.perform(post("/api/mcp/answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4001))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("question")));
    }

    @Test
    void should_return_400_when_question_missing() throws Exception {
        // 请求体缺少 question 字段
        mockMvc.perform(post("/api/mcp/answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4001));
    }

    @Test
    void should_return_5001_when_tool_calling_exhausted() throws Exception {
        // Agent 抛 ToolCallingExhaustedException,被 GlobalExceptionHandler 捕获
        when(agent.answer(anyString())).thenThrow(new ToolCallingExhaustedException(
                5, List.of(), List.of()));

        Map<String, String> body = Map.of("question", "查");
        mockMvc.perform(post("/api/mcp/answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5001))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("5")));
    }

    @Test
    void should_return_5000_on_unexpected_runtime_exception() throws Exception {
        when(agent.answer(anyString())).thenThrow(new RuntimeException("boom"));

        Map<String, String> body = Map.of("question", "查");
        mockMvc.perform(post("/api/mcp/answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(5000))
                // 兜底响应不泄露原始异常 message
                .andExpect(jsonPath("$.message").value("服务内部错误"));
    }
}
