package com.example.claudedemo.agent.session;

/**
 * Token 用量计数器(预留).
 *
 * <p><b>V2 第五阶段</b>:在 {@link AgentSession} 中作为 token 累加点,目前
 * 由 Agent 在 LLM 调用后调用 {@link #addPrompt(long)} / {@link #addCompletion(long)}。
 *
 * <p><b>未连真实 LLM usage</b>:V1 阶段 {@code LlmClient.chatWithTools} 不返回 usage
 * 字段,本类仅作为"被加数"存在;待 V2 后续阶段 LlmResponse 支持 usage 字段后,
 * Agent 在响应中直接 {@code session.tokenUsage().addPrompt(resp.usage().promptTokens())}。
 *
 * <p><b>类型</b>:全部使用 {@code long},避免高 token 量场景下整数溢出
 * (单次会话百万级 token 仍安全)。
 *
 * <p><b>线程安全</b>:用 {@code synchronized} 保护所有写操作,保证并发安全。
 *
 * @since 0.0.1
 */
public class TokenUsage {

    private long promptTokens;
    private long completionTokens;
    private long totalTokens;

    /**
     * 构造全零计数器.
     */
    public TokenUsage() {
    }

    /**
     * 复制构造器(深拷贝).
     */
    public TokenUsage(TokenUsage other) {
        if (other == null) {
            return;
        }
        this.promptTokens = other.promptTokens;
        this.completionTokens = other.completionTokens;
        this.totalTokens = other.totalTokens;
    }

    /**
     * 累加 prompt tokens,totalTokens 同步增加.
     */
    public synchronized void addPrompt(long n) {
        if (n < 0) {
            throw new IllegalArgumentException("n 必须 >= 0, 实际: " + n);
        }
        this.promptTokens += n;
        this.totalTokens += n;
    }

    /**
     * 累加 completion tokens,totalTokens 同步增加.
     */
    public synchronized void addCompletion(long n) {
        if (n < 0) {
            throw new IllegalArgumentException("n 必须 >= 0, 实际: " + n);
        }
        this.completionTokens += n;
        this.totalTokens += n;
    }

    /**
     * 合并另一个 TokenUsage 到本对象;null 入参视为 no-op.
     */
    public synchronized void add(TokenUsage other) {
        if (other == null) {
            return;
        }
        this.promptTokens += other.promptTokens;
        this.completionTokens += other.completionTokens;
        this.totalTokens += other.totalTokens;
    }

    /**
     * 全部归零.
     */
    public synchronized void reset() {
        this.promptTokens = 0L;
        this.completionTokens = 0L;
        this.totalTokens = 0L;
    }

    public synchronized long promptTokens() {
        return promptTokens;
    }

    public synchronized long completionTokens() {
        return completionTokens;
    }

    public synchronized long totalTokens() {
        return totalTokens;
    }

    @Override
    public synchronized String toString() {
        return "TokenUsage{prompt=" + promptTokens
                + ", completion=" + completionTokens
                + ", total=" + totalTokens + "}";
    }
}
