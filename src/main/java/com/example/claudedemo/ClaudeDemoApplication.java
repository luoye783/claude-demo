package com.example.claudedemo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Claude Demo 启动类.
 *
 * <p>本项目用于学习 Claude Code、MCP、Agent 的使用与开发。
 *
 * @author claude-code
 * @since 0.0.1
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ClaudeDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClaudeDemoApplication.class, args);
    }
}
