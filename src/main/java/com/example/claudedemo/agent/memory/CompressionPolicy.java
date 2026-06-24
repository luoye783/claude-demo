package com.example.claudedemo.agent.memory;

/**
 * 摘要压缩策略抽象.
 *
 * <p>由 {@link InMemoryConversationStore#appendTurn} 调用,根据当前
 * {@link ConversationMemory#size()} 决定是否触发 {@link MemoryCompressor},
 * 以及压缩后保留多少条近期 turn。
 *
 * <p>V1 仅提供基于 turn 数量的策略 ({@link TurnCountCompressionPolicy});
 * 后续可扩展基于 token 数量、基于时间窗、基于显式调用等的策略。
 *
 * @since 0.0.1
 */
public interface CompressionPolicy {

    /**
     * 当前 turn 数达到压缩条件.
     *
     * @param currentSize 当前 {@link ConversationMemory#size()}
     * @return true 表示应当触发压缩
     */
    boolean shouldCompress(int currentSize);

    /**
     * 压缩后保留的近期 turn 数量.
     *
     * <p>压缩时,turn[0..size-keepRecentTurns) 会被淘汰,用于驱动摘要生成;
     * 剩余 turn[size-keepRecentTurns..size) 仍保留在 memory 中,供后续 LLM 上下文使用。
     *
     * @return 必须 &gt;= 0 且 &lt; 触发压缩的阈值
     */
    int keepRecentTurns();
}
