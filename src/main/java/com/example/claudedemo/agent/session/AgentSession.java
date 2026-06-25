package com.example.claudedemo.agent.session;

import com.example.claudedemo.agent.memory.ConversationMemory;
import com.example.claudedemo.agent.trace.AgentTrace;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Agent 一次会话的运行时上下文(V2 第五阶段).
 *
 * <p>把"一次 Agent 调用涉及的所有运行时状态"打包成统一对象:
 * <ul>
 *   <li>{@link #sessionId}  — 会话 ID;无记忆模式下可为 {@code null}</li>
 *   <li>{@link #memory}     — 短期会话记忆;无记忆模式下为 {@code null},通过 {@link #hasMemory()} 判断</li>
 *   <li>{@link #trace}      — 执行轨迹,本对象内始终非空</li>
 *   <li>{@link #tokenUsage} — token 计数器(预留,V1 阶段未连真实 LLM usage)</li>
 *   <li>{@link #metadata}   — 自由扩展元信息,key 不允许 null</li>
 *   <li>{@link #createdAtMs}— 创建时间戳({@code System.currentTimeMillis()}),便于观测 session 生命周期</li>
 * </ul>
 *
 * <p><b>解耦原则</b>:本类不继承 {@link ConversationMemory} 也不继承 {@link AgentTrace},
 * 仅以引用方式组合;memory 与 trace 各自的演化互不干扰。
 *
 * <p><b>不可变 vs 可变</b>:
 * <ul>
 *   <li>{@code sessionId / memory / trace / createdAtMs} 引用与值在构造时确定,之后不变</li>
 *   <li>{@code tokenUsage} 通过 {@link TokenUsage#addPrompt(long)} 等方法累加</li>
 *   <li>{@code metadata} 通过 {@link #put(String, Object)} / {@link #get(String)} 操作,
 *       内部用 {@code HashMap} + 同步块保护,允许 null value,不允许 null key</li>
 * </ul>
 *
 * <p><b>Builder</b>:提供 {@link #builder()} 便于测试与未来扩展字段;必填字段为 sessionId、
 * memory(可空)、trace;tokenUsage 与 metadata 缺省自动初始化。
 *
 * @since 0.0.1
 */
public class AgentSession {

    private final String sessionId;
    private final ConversationMemory memory;
    private final AgentTrace trace;
    private final TokenUsage tokenUsage;
    private final Map<String, Object> metadata;
    private final long createdAtMs;

    /**
     * 必填字段构造器(常用).
     *
     * <p>tokenUsage 与 metadata 自动初始化,createdAtMs 取系统当前时间。
     */
    public AgentSession(String sessionId, ConversationMemory memory, AgentTrace trace) {
        this(sessionId, memory, trace, new TokenUsage(), new HashMap<>(),
                System.currentTimeMillis());
    }

    /**
     * 全字段构造器(供 Builder / 测试使用).
     */
    public AgentSession(String sessionId,
                        ConversationMemory memory,
                        AgentTrace trace,
                        TokenUsage tokenUsage,
                        Map<String, Object> metadata,
                        long createdAtMs) {
        this.sessionId = sessionId;
        this.memory = memory;
        this.trace = trace;
        this.tokenUsage = (tokenUsage == null) ? new TokenUsage() : tokenUsage;
        this.metadata = (metadata == null) ? new HashMap<>() : metadata;
        this.createdAtMs = createdAtMs;
    }

    public String sessionId() {
        return sessionId;
    }

    public ConversationMemory memory() {
        return memory;
    }

    public AgentTrace trace() {
        return trace;
    }

    public TokenUsage tokenUsage() {
        return tokenUsage;
    }

    public long createdAtMs() {
        return createdAtMs;
    }

    /**
     * 是否携带有效 memory(非 null 视为有效).
     */
    public boolean hasMemory() {
        return memory != null;
    }

    /**
     * 写入元数据;key 不允许 null,value 允许 null.
     */
    public synchronized void put(String key, Object value) {
        if (key == null) {
            throw new IllegalArgumentException("metadata key must not be null");
        }
        metadata.put(key, value);
    }

    /**
     * 读取元数据;key 不存在或 value 为 null 时返回 null.
     */
    public synchronized Object get(String key) {
        if (key == null) {
            return null;
        }
        return metadata.get(key);
    }

    /**
     * 是否包含指定 key.
     */
    public synchronized boolean contains(String key) {
        if (key == null) {
            return false;
        }
        return metadata.containsKey(key);
    }

    /**
     * 返回 metadata 的不可变快照,防止外部绕过 put 直接修改底层 map.
     */
    public synchronized Map<String, Object> metadataView() {
        return Collections.unmodifiableMap(new HashMap<>(metadata));
    }

    /**
     * 新建 Builder;sessionId 为必填,其他字段可后续填充或取默认.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * {@link AgentSession} 构造器,便于测试与未来扩展.
     */
    public static final class Builder {
        private String sessionId;
        private ConversationMemory memory;
        private AgentTrace trace;
        private TokenUsage tokenUsage;
        private Map<String, Object> metadata;
        private Long createdAtMs;

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder memory(ConversationMemory memory) {
            this.memory = memory;
            return this;
        }

        public Builder trace(AgentTrace trace) {
            this.trace = trace;
            return this;
        }

        public Builder tokenUsage(TokenUsage tokenUsage) {
            this.tokenUsage = tokenUsage;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder createdAtMs(long createdAtMs) {
            this.createdAtMs = createdAtMs;
            return this;
        }

        public AgentSession build() {
            AgentTrace t = (trace == null) ? new AgentTrace() : trace;
            // createdAtMs: 显式指定时用之,否则取系统当前时间(与 3-arg 构造器一致)
            long created = (createdAtMs == null) ? System.currentTimeMillis() : createdAtMs;
            return new AgentSession(sessionId, memory, t, tokenUsage, metadata, created);
        }
    }
}
