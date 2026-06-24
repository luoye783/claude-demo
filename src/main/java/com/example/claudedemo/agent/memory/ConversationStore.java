package com.example.claudedemo.agent.memory;

import com.example.claudedemo.agent.trace.AgentTrace;

import java.util.Optional;

/**
 * 会话记忆存储接口.
 *
 * <p>按 {@code sessionId} 存取 {@link ConversationMemory},负责记忆的查找与创建。
 * V1 默认实现为 {@link InMemoryConversationStore}(ConcurrentHashMap);
 * V2 可替换为 RedisConversationStore 等持久化实现。
 *
 * <p><b>V2 第四阶段扩展</b>:新增 {@link #appendTurn(String, ConversationTurn, AgentTrace)},
 * 统一管理"追加 + 压缩"流程。Agent 不再直接调 {@code ConversationMemory.addTurn},
 * 而是调本方法 — 由 store 内部根据 {@link CompressionPolicy} 决定是否调用
 * {@link MemoryCompressor} 生成新摘要,再丢弃被淘汰的旧 turn。
 *
 * <p>V1 不提供 {@code delete(sessionId)} 方法,由 V2 引入 TTL / 淘汰策略时一同加入。
 *
 * @since 0.0.1
 */
public interface ConversationStore {

    /**
     * 获取或创建指定 session 的记忆.
     *
     * <p>不存在时自动创建空 memory,保证返回非 null。
     *
     * @param sessionId 会话 ID
     * @return 对应 session 的 ConversationMemory(不会为 null)
     */
    ConversationMemory getOrCreate(String sessionId);

    /**
     * 查找指定 session 的记忆,不存在时返回 {@link Optional#empty()}.
     */
    Optional<ConversationMemory> find(String sessionId);

    /**
     * 当前 store 中管理的 session 数量(测试/调试用).
     */
    int size();

    /**
     * 追加一轮对话(由 store 统一管理压缩).
     *
     * <p>等价于: 若当前 memory 满足压缩条件,先调 {@link MemoryCompressor} 生成新
     * {@link SummaryMemory} 并丢弃被淘汰的 turn,再追加本轮 turn。
     *
     * <p>压缩是否触发完全由 {@link CompressionPolicy} 与 memory 当前 size 决定;
     * 失败时(MemoryCompressor 返回 null)压缩被跳过,memory 保持原样,本轮 turn
     * 仍正常追加。
     *
     * @param sessionId 会话 ID,不可为空
     * @param turn      本轮对话
     * @param trace     Agent 轨迹(用于记录 {@code MEMORY_COMPRESS} 步骤);可为 null
     * @throws IllegalArgumentException 当 sessionId 为空
     */
    void appendTurn(String sessionId, ConversationTurn turn, AgentTrace trace);
}
