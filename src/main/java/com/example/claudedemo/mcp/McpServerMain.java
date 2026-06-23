package com.example.claudedemo.mcp;

import com.example.claudedemo.mcp.server.McpServerConfig;
import com.example.claudedemo.mcp.server.McpServerFactory;
import com.example.claudedemo.mcp.server.McpServerMetadata;
import io.modelcontextprotocol.server.McpSyncServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.concurrent.CountDownLatch;

/**
 * MCP Server 入口.
 *
 * <p>启动一个 <b>非 Web</b> 的 Spring 上下文(只扫描 {@code com.example.claudedemo.mcp} 包),
 * 构造 {@link McpServerFactory},阻塞主线程直到收到 shutdown 信号(SIGINT / SIGTERM / stdin EOF).
 *
 * <p><b>V1 范围(Step 1)</b>:仅启动空 server,工具列表为空,用于验证:
 * <ol>
 *   <li>MCP SDK 集成正确,握手成功</li>
 *   <li>Claude Code {@code /mcp} 能看到 {@code claude-demo-db}</li>
 *   <li>工具数为 0</li>
 * </ol>
 *
 * <p><b>关键设计:不用 {@code @SpringBootApplication}</b>(原因见 {@link McpServerConfig}).
 * 改用 {@code SpringApplicationBuilder.sources(McpServerConfig.class)} 显式指定配置源,
 * 不会污染其它 Spring 上下文(如 {@code ClaudeDemoApplication} 的测试).
 *
 * <p><b>启动命令</b>:
 * <pre>
 * mvn exec:java \
 *   -Dexec.mainClass=com.example.claudedemo.mcp.McpServerMain \
 *   -Dexec.cleanupDaemonThreads=false \
 *   -Dspring.main.web-application-type=none \
 *   -Dlogback.configurationFile=logback-mcp.xml
 * </pre>
 *
 * @author claude-code
 * @since 0.0.1
 */
public class McpServerMain {

    private static final Logger log = LoggerFactory.getLogger(McpServerMain.class);

    /**
     * MCP Server 进程入口.
     *
     * @param args 启动参数(当前未使用,保留以备扩展)
     */
    public static void main(String[] args) {
        // 必须在 SpringApplicationBuilder 之前设置,Spring Boot 才会用它
        // (Spring Boot 覆盖 logback.configurationFile,要用 logging.config)
        System.setProperty("logging.config", "classpath:logback-mcp.xml");

        // 用 stderr 直写(此时 logback-mcp.xml 还没生效,logback 默认会走 stdout)
        System.err.println("[mcp-server] Starting " + McpServerMetadata.SERVER_NAME
                + " v" + McpServerMetadata.SERVER_VERSION);

        // 1. 启动 Spring 上下文(只加载 McpServerConfig,非 Web 模式,禁 banner)
        ConfigurableApplicationContext ctx = new SpringApplicationBuilder()
                .sources(McpServerConfig.class)
                .web(WebApplicationType.NONE)
                .bannerMode(Banner.Mode.OFF)
                .logStartupInfo(false)
                .run(args);

        // 2. 启动 MCP server
        McpServerFactory factory = ctx.getBean(McpServerFactory.class);
        McpSyncServer server = factory.start();

        // 3. 阻塞主线程,等待 shutdown 信号
        CountDownLatch shutdownLatch = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown signal received. Closing MCP server...");
            try {
                server.close();
            } catch (Exception e) {
                log.warn("Error closing MCP server: {}", e.toString());
            } finally {
                shutdownLatch.countDown();
            }
        }, "mcp-shutdown-hook"));

        log.info("MCP server is running. Press Ctrl+C to stop, or close stdin to disconnect.");

        try {
            shutdownLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Main thread interrupted, exiting");
        } finally {
            ctx.close();
        }
    }
}
