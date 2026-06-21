package com.example.claudedemo.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Nl2SqlAgent 手动集成测试：真实 LLM + 真实 H2 内存库.
 *
 * <p>默认 {@code mvn test} 会<b>自动跳过</b>。运行命令：
 * <pre>{@code
 * RUN_LLM_IT=true mvn test -Dtest=Nl2SqlAgentManualIT
 * }</pre>
 *
 * <p>运行前需确保：
 * <ul>
 *   <li>{@code LLM_API_KEY} 环境变量已设置（或 application.yml 中配置）</li>
 *   <li>网络能访问 LLM 接口</li>
 * </ul>
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_LLM_IT", matches = "true")
@Disabled
class Nl2SqlAgentManualIT {

    @Autowired private Nl2SqlAgent agent;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS USERS");
        jdbcTemplate.execute("CREATE TABLE USERS (id INT NOT NULL, name VARCHAR(50), age INT)");
        jdbcTemplate.update("INSERT INTO USERS VALUES (1, 'Alice', 30)");
        jdbcTemplate.update("INSERT INTO USERS VALUES (2, 'Bob', 25)");
        jdbcTemplate.update("INSERT INTO USERS VALUES (3, 'Charlie', 35)");
    }

    @Test
    void should_run_full_pipeline_with_real_llm() {
        String question = "用户表有多少人？";

        AgentResult result = agent.answer(question);

        // 打印完整链路到控制台
        System.out.println("===== Nl2SqlAgent Manual IT =====");
        System.out.println("Question:      " + result.question());
        System.out.println("Generated SQL: " + result.generatedSql());
        System.out.println("Validated SQL: " + result.validatedSql().sql());
        System.out.println("Rows:          " + result.rows());
        System.out.println("Final Answer:  " + result.answer());
        System.out.println("=================================");
    }
}
