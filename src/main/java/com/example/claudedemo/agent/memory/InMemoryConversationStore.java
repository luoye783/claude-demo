package com.example.claudedemo.agent.memory;

import com.example.claudedemo.agent.trace.AgentTrace;
import com.example.claudedemo.agent.trace.StepType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存版 {@link ConversationStore},基于 {@link ConcurrentHashMap}.
 *
 * <p>V1 默认实现,不连接 Redis / MySQL,适合单机学习与测试场景。
 * V2 可替换为 RedisConversationStore 等分布式实现。
 *
 * <p><b>V2 第四阶段:压缩</b>
 * <ul>
 *   <li>{@link #appendTurn} 在追加 turn 之前,根据 {@link CompressionPolicy} 判断是否触发压缩</li>
 *   <li>触发时:收集待淘汰 turn → 调 {@link MemoryCompressor} → 写回新 summary → 丢弃老 turn → 记录 trace</li>
 *   <li>{@link MemoryCompressor} / {@link CompressionPolicy} 缺失时降级为纯追加(FIFO),不抛异常</li>
 *   <li>线程安全：{@link #getOrCreate(String)} 使用 {@code computeIfAbsent} 保证并发安全;
 *       {@code appendTurn} 内部在 memory 自身 synchronized 块中完成压缩 + 追加,保证 per-session 原子</li>
 * </ul>
 *
 * @since 0.0.1
 */
@Component
public class InMemoryConversationStore implements ConversationStore {

    private static final Logger log = LoggerFactory.getLogger(InMemoryConversationStore.class);

    private final ConcurrentHashMap<String, ConversationMemory> store = new ConcurrentHashMap<>();
    /** 压缩器 provider — 用 ObjectProvider 以兼容"未装配 compressor"的旧用法. */
    private final ObjectProvider<MemoryCompressor> compressorProvider;
    /** 压缩策略(可为 null,null 时不压缩). */
    private final CompressionPolicy policy;

    /**
     * V1 兼容构造器:无压缩器、无策略,等同于 V1 行为(纯 FIFO).
     *
     * <p>测试场景或不需要压缩时使用。
     */
    public InMemoryConversationStore() {
        this((MemoryCompressor) null, null);
    }

    /**
     * V2 完整构造器.
     *
     * @param compressor 压缩器(可为 null,代表不压缩)
     * @param policy     压缩策略(可为 null,代表不压缩)
     */
    public InMemoryConversationStore(MemoryCompressor compressor, CompressionPolicy policy) {
        this.compressorProvider = (compressor == null) ? null : new SingletonProvider(compressor);
        this.policy = policy;
    }

    /**
     * Spring 注入推荐构造器.
     *
     * <p>{@link ObjectProvider} 让 compressor 变为可选依赖:未装配时不压缩。
     */
    public InMemoryConversationStore(ObjectProvider<MemoryCompressor> compressorProvider,
                                     CompressionPolicy policy) {
        this.compressorProvider = compressorProvider;
        this.policy = policy;
    }

    @Override
    public ConversationMemory getOrCreate(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        return store.computeIfAbsent(sessionId, ConversationMemory::new);
    }

    @Override
    public Optional<ConversationMemory> find(String sessionId) {
        return Optional.ofNullable(store.get(sessionId));
    }

    @Override
    public int size() {
        return store.size();
    }

    @Override
    public void appendTurn(String sessionId, ConversationTurn turn, AgentTrace trace) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        ConversationMemory memory = getOrCreate(sessionId);
        if (turn == null) {
            return;
        }
        // 先 addTurn 再判断压缩,语义: turn 数达到阈值后压缩
        memory.executeUnderLock(() -> {
            memory.addTurn(turn);
            maybeCompress(memory, trace);
        });
    }

    /**
     * 根据策略判断是否压缩;若触发,调 compressor 并写回 summary / 淘汰 turn / 记 trace.
     */
    private void maybeCompress(ConversationMemory memory, AgentTrace trace) {
        if (policy == null || compressorProvider == null) {
            return;
        }
        MemoryCompressor compressor = compressorProvider.getIfAvailable();
        if (compressor == null) {
            return;
        }
        int currentSize = memory.size();
        if (!policy.shouldCompress(currentSize)) {
            return;
        }
        int keepRecent = policy.keepRecentTurns();
        List<ConversationTurn> evicted = memory.turnsToEvict(keepRecent);
        if (evicted.isEmpty()) {
            return;
        }
        long start = System.currentTimeMillis();
        SummaryMemory oldSummary = memory.summary();
        SummaryMemory newSummary = compressor.compress(oldSummary, evicted);
        long durationMs = System.currentTimeMillis() - start;

        if (newSummary == null) {
            log.warn("会话记忆压缩失败,降级保留原状态: sessionId={}, evicted={}, durationMs={}",
                    memory.sessionId(), evicted.size(), durationMs);
            if (trace != null) {
                trace.addError("记忆压缩失败,降级保留原状态 (evicted=" + evicted.size() + ")");
            }
            return;
        }

        memory.setSummary(newSummary);
        memory.dropOldest(evicted.size());
        log.info("会话记忆压缩完成: sessionId={}, version={}->{}, evicted={}, kept={}, durationMs={}",
                memory.sessionId(), oldSummary.version(), newSummary.version(),
                evicted.size(), memory.size(), durationMs);
        if (trace != null) {
            trace.addStep(StepType.MEMORY_COMPRESS,
                    "evicted=" + evicted.size()
                            + ", version=" + oldSummary.version() + "->" + newSummary.version()
                            + ", summary=" + newSummary.summary().length() + "chars",
                    durationMs);
        }
    }

    /**
     * 极简 {@link ObjectProvider},用于在非 Spring 场景手工注入单实例 compressor.
     *
     * <p>仅实现本类用到的 {@link ObjectProvider#getIfAvailable()};
     * 其余方法不被调用,因此抛 {@link UnsupportedOperationException}。
     */
    private static final class SingletonProvider implements ObjectProvider<MemoryCompressor> {
        private final MemoryCompressor instance;

        SingletonProvider(MemoryCompressor instance) {
            this.instance = instance;
        }

        @Override
        public MemoryCompressor getIfAvailable() {
            return instance;
        }

        @Override
        public MemoryCompressor getObject() {
            return instance;
        }

        @Override
        public MemoryCompressor getObject(Object... args) {
            throw new UnsupportedOperationException();
        }

        @Override
        public MemoryCompressor getIfUnique() {
            return instance;
        }

        @Override
        public java.util.stream.Stream<MemoryCompressor> stream() {
            return java.util.stream.Stream.of(instance);
        }

        @Override
        public java.util.stream.Stream<MemoryCompressor> orderedStream() {
            return java.util.stream.Stream.of(instance);
        }
    }
}
