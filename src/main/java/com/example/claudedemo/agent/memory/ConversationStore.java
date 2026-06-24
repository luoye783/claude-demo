package com.example.claudedemo.agent.memory;

import java.util.Optional;

/**
 * 会话记忆存储接口.
 *
 * <p>按 {@code sessionId} 存取 {@link ConversationMemory},负责记忆的查找与创建。
 * V1 默认实现为 {@link InMemoryConversationStore}(ConcurrentHashMap);
 * V2 可替换为 RedisConversationStore 等持久化实现。
 *
 * <p>V1 不提供 {@code delete(sessionId)} 方法,由 V2 引入 TTL / 淘汰策略时一同加入。
 *
 * @since 0.0.1
 */
public interface ConversationStore {

    /**
     * 获取或创建指定 session 的记忆。
     *
     * <p>不存在时自动创建空 memory,保证返回非 null。
     *
     * @param sessionId 会话 ID
     * @return 对应 session 的 ConversationMemory(不会为 null)
     */
    ConversationMemory getOrCreate(String sessionId);

    /**
     * 查找指定 session 的记忆,不存在时返回 {@link Optional#empty()}。
     */
    Optional<ConversationMemory> find(String sessionId);

    /**
     * 当前 store 中管理的 session 数量(测试/调试用).
     */
    int size();
}
