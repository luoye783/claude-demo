package com.example.claudedemo.agent;

import com.example.claudedemo.llm.ChatMessage;
import com.example.claudedemo.llm.LlmClient;
import com.example.claudedemo.llm.LlmResponse;
import com.example.claudedemo.sql.SchemaIntrospector;
import com.example.claudedemo.sql.SqlExecutor;
import com.example.claudedemo.sql.SqlValidator;
import com.example.claudedemo.sql.ValidatedSql;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Nl2SqlAgent 测试：H2 + 真 SchemaSelector/SqlValidator/SqlExecutor + mock LlmClient.
 *
 * <p>说明：Java 26 上 Mockito 5.11 mock SchemaSelector 会失败（ByteBuddy 不能改 Object）。
 * 所以 SchemaSelector / SqlExecutor / SqlValidator 用真 H2 实例，只 mock LlmClient。
 */
@JdbcTest
@Import({SchemaIntrospector.class, com.example.claudedemo.agent.SchemaSelector.class,
        SqlExecutor.class, SqlValidator.class})
class Nl2SqlAgentTest {

    @Autowired private com.example.claudedemo.agent.SchemaSelector schemaSelector;
    @Autowired private SqlValidator sqlValidator;
    @Autowired private SqlExecutor sqlExecutor;
    @Autowired private JdbcTemplate jdbcTemplate;

    private LlmClient llmClient;
    private final PromptBuilder promptBuilder = new PromptBuilder();
    private Nl2SqlAgent agent;

    @BeforeEach
    void setUp() {
        // 准备 H2 测试数据
        jdbcTemplate.execute("DROP TABLE IF EXISTS USERS");
        jdbcTemplate.execute("CREATE TABLE USERS (id INT NOT NULL, name VARCHAR(50), age INT)");
        jdbcTemplate.update("INSERT INTO USERS VALUES (1, 'Alice', 30)");
        jdbcTemplate.update("INSERT INTO USERS VALUES (2, 'Bob', 25)");
        jdbcTemplate.update("INSERT INTO USERS VALUES (3, 'Charlie', 35)");

        // mock LlmClient（LlmClient 是非 final 类，Mockito Subclass maker 可以 mock）
        llmClient = mock(LlmClient.class);

        agent = new Nl2SqlAgent(schemaSelector, llmClient, promptBuilder, sqlValidator, sqlExecutor);
    }

    @Test
    void should_run_two_step_loop_and_return_agent_result() {
        when(llmClient.chat(anyList()))
                .thenReturn(new LlmResponse("SELECT COUNT(*) FROM USERS", "stop"))
                .thenReturn(new LlmResponse("共有 3 个用户。", "stop"));

        AgentResult result = agent.answer("用户表有多少人？");

        assertEquals("用户表有多少人？", result.question());
        assertEquals("SELECT COUNT(*) FROM USERS", result.generatedSql());
        assertEquals("SELECT COUNT(*) FROM USERS LIMIT 100", result.validatedSql().sql());
        assertEquals(1, result.rows().size());
        assertEquals("共有 3 个用户。", result.answer());
        verify(llmClient, times(2)).chat(anyList());
    }

    @Test
    void should_call_prompt_builder_for_both_prompts() {
        when(llmClient.chat(anyList()))
                .thenReturn(new LlmResponse("SELECT * FROM USERS", "stop"))
                .thenReturn(new LlmResponse("用户列表", "stop"));

        agent.answer("列出所有用户");

        ArgumentCaptor<List<ChatMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(llmClient, times(2)).chat(captor.capture());

        // 第 1 次 LLM：生成 SQL
        List<ChatMessage> firstCall = captor.getAllValues().get(0);
        assertEquals("system", firstCall.get(0).role());
        assertTrue(firstCall.get(0).content().contains("SQL 生成器"));

        // 第 2 次 LLM：生成答案
        List<ChatMessage> secondCall = captor.getAllValues().get(1);
        assertEquals("system", secondCall.get(0).role());
        assertTrue(secondCall.get(0).content().contains("数据分析助手"));
    }

    @Test
    void should_pass_executed_rows_to_second_llm() {
        when(llmClient.chat(anyList()))
                .thenReturn(new LlmResponse("SELECT * FROM USERS", "stop"))
                .thenReturn(new LlmResponse("用户列表", "stop"));

        agent.answer("列出所有用户");

        ArgumentCaptor<List<ChatMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(llmClient, times(2)).chat(captor.capture());

        // 第 2 次 LLM 调用的 user prompt 应包含行数据
        List<ChatMessage> secondCall = captor.getAllValues().get(1);
        String userContent = secondCall.get(1).content();
        assertTrue(userContent.contains("Alice"), "user prompt 应包含 Alice");
        assertTrue(userContent.contains("Bob"), "user prompt 应包含 Bob");
    }

    // ==================== V1 SQL 自修复 ====================

    @Test
    void should_record_single_step_when_first_round_succeeds() {
        when(llmClient.chat(anyList()))
                .thenReturn(new LlmResponse("SELECT COUNT(*) FROM USERS", "stop"))
                .thenReturn(new LlmResponse("共有 3 个用户。", "stop"));

        AgentResult result = agent.answer("用户表有多少人？");

        assertEquals(1, result.steps().size(), "首轮成功时 steps 只有 1 条");
        AgentStep step = result.steps().get(0);
        assertEquals(1, step.round());
        assertTrue(step.success());
        assertNull(step.errorMessage());
        assertNotNull(step.validatedSql());
        // 首轮即成功:generatedSql 与 finalGeneratedSql 相同
        assertEquals(result.generatedSql(), result.finalGeneratedSql());
    }

    @Test
    void should_retry_after_validation_failure_and_succeed_on_second_round() {
        // 第 1 轮:返回不可解析的 SQL 触发 SqlValidationException(SQL_PARSE_ERROR)
        // 第 2 轮:返回正确 SQL
        // 第 3 轮:返回答案
        when(llmClient.chat(anyList()))
                .thenReturn(new LlmResponse("NOT_A_VALID_SQL", "stop"))
                .thenReturn(new LlmResponse("SELECT COUNT(*) FROM USERS", "stop"))
                .thenReturn(new LlmResponse("共有 3 个用户。", "stop"));

        AgentResult result = agent.answer("用户表有多少人？");

        assertEquals(2, result.steps().size());
        // 第 1 轮:校验失败
        AgentStep fail = result.steps().get(0);
        assertEquals(1, fail.round());
        assertTrue(!fail.success());
        assertNull(fail.validatedSql(), "校验失败时 validatedSql 应为 null");
        assertTrue(fail.errorMessage().contains("SQL_PARSE_ERROR"),
                "校验失败 errorMessage 应包含 SQL_PARSE_ERROR,实际:" + fail.errorMessage());
        // 第 2 轮:成功
        AgentStep ok = result.steps().get(1);
        assertEquals(2, ok.round());
        assertTrue(ok.success());
        assertNotNull(ok.validatedSql());

        // generatedSql 仍是首轮坏 SQL,finalGeneratedSql 才是最终成功轮
        assertEquals("NOT_A_VALID_SQL", result.generatedSql());
        assertEquals("SELECT COUNT(*) FROM USERS", result.finalGeneratedSql());

        verify(llmClient, times(3)).chat(anyList());

        // 第 2 次 LLM 调用的 user 段应包含上一轮失败 SQL 与错误信息
        ArgumentCaptor<List<ChatMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(llmClient, times(3)).chat(captor.capture());
        List<ChatMessage> repairCall = captor.getAllValues().get(1);
        String repairUser = repairCall.get(1).content();
        assertTrue(repairUser.contains("NOT_A_VALID_SQL"),
                "修复 prompt 应包含上一轮失败的 SQL,实际:" + repairUser);
        assertTrue(repairUser.contains("SQL_PARSE_ERROR"),
                "修复 prompt 应包含错误码,实际:" + repairUser);
    }

    @Test
    void should_retry_after_execution_failure_and_succeed_on_second_round() {
        // 第 1 轮:SQL 合法但表不存在,H2 抛 BadSqlGrammarException(DataAccessException 子类)
        // 第 2 轮:返回正确 SQL
        // 第 3 轮:返回答案
        when(llmClient.chat(anyList()))
                .thenReturn(new LlmResponse("SELECT * FROM NOT_EXIST", "stop"))
                .thenReturn(new LlmResponse("SELECT COUNT(*) FROM USERS", "stop"))
                .thenReturn(new LlmResponse("共有 3 个用户。", "stop"));

        AgentResult result = agent.answer("用户表有多少人？");

        assertEquals(2, result.steps().size());
        // 第 1 轮:校验通过但执行失败
        AgentStep fail = result.steps().get(0);
        assertEquals(1, fail.round());
        assertTrue(!fail.success());
        assertNotNull(fail.validatedSql(), "执行失败时 validatedSql 不为 null(校验已通过)");
        assertTrue(fail.errorMessage().contains("BadSqlGrammarException"),
                "执行失败 errorMessage 应包含 BadSqlGrammarException,实际:" + fail.errorMessage());
        // 第 2 轮:成功
        assertTrue(result.steps().get(1).success());

        assertEquals("SELECT * FROM NOT_EXIST", result.generatedSql());
        assertEquals("SELECT COUNT(*) FROM USERS", result.finalGeneratedSql());
    }

    @Test
    void should_throw_sql_generation_failed_exception_after_max_retries() {
        // 4 轮全部返回不可解析的 SQL
        when(llmClient.chat(anyList()))
                .thenReturn(new LlmResponse("NOT_A_VALID_SQL", "stop"));

        SqlGenerationFailedException ex = assertThrows(
                SqlGenerationFailedException.class,
                () -> agent.answer("用户表有多少人？"));

        // 异常 message 不含 SQL 文本,仅含摘要
        assertTrue(ex.getMessage().contains("已尝试 4 轮"),
                "异常 message 应含尝试轮次,实际:" + ex.getMessage());
        assertTrue(!ex.getMessage().contains("NOT_A_VALID_SQL"),
                "异常 message 不应包含 SQL 文本");

        // 异常携带全部 4 轮步骤
        assertEquals(4, ex.getSteps().size());
        for (int i = 0; i < 4; i++) {
            AgentStep step = ex.getSteps().get(i);
            assertEquals(i + 1, step.round());
            assertTrue(!step.success());
            assertTrue(step.errorMessage().contains("SQL_PARSE_ERROR"));
        }

        // LLM 恰好被调 4 次(全部 SQL 生成轮),未调答案生成轮
        verify(llmClient, times(4)).chat(anyList());
    }
}
