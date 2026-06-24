package com.example.claudedemo.agent.memory;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存版 {@link ConversationStore},基于 {@link ConcurrentHashMap}.
 *
 * <p>V1 默认实现,不连接 Redis / MySQL,适合单机学习与测试场景。
 * V2 可替换为 RedisConversationStore 等分布式实现。
 *
 * <p>线程安全：{@link #getOrCreate(String)} 使用 {@code computeIfAbsent} 保证并发安全。
 *
 * @since 0.0.1
 */
@Component
public class InMemoryConversationStore implements ConversationStore {

    private final ConcurrentHashMap<String, ConversationMemory> store = new ConcurrentHashMap<>();

    @Override
    public ConversationMemory getOrCreate(String sessionId) {
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId must not be null");
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
}
