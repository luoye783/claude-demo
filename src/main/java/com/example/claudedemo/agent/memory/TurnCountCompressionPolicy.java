package com.example.claudedemo.agent.memory;

/**
 * 基于 turn 数量的压缩策略.
 *
 * <p>当 {@code currentSize >= threshold} 时触发压缩;压缩后保留最近
 * {@link #keepRecentTurns} 条 turn,其余喂给 {@link MemoryCompressor}。
 *
 * <p>不变量:
 * <ul>
 *   <li>{@code threshold > 0}</li>
 *   <li>{@code keepRecent >= 0}</li>
 *   <li>{@code keepRecent < threshold} —— 否则压缩后仍会立即再次触发</li>
 * </ul>
 *
 * @since 0.0.1
 */
public class TurnCountCompressionPolicy implements CompressionPolicy {

    private final int threshold;
    private final int keepRecent;

    /**
     * @param threshold   压缩触发阈值(memory.size() &gt;= threshold 时触发)
     * @param keepRecent  压缩后保留的近期 turn 数
     */
    public TurnCountCompressionPolicy(int threshold, int keepRecent) {
        if (threshold <= 0) {
            throw new IllegalArgumentException("threshold 必须 > 0, 实际: " + threshold);
        }
        if (keepRecent < 0) {
            throw new IllegalArgumentException("keepRecent 必须 >= 0, 实际: " + keepRecent);
        }
        if (keepRecent >= threshold) {
            throw new IllegalArgumentException(
                    "keepRecent 必须 < threshold, 实际 keepRecent=" + keepRecent + ", threshold=" + threshold);
        }
        this.threshold = threshold;
        this.keepRecent = keepRecent;
    }

    @Override
    public boolean shouldCompress(int currentSize) {
        return currentSize >= threshold;
    }

    @Override
    public int keepRecentTurns() {
        return keepRecent;
    }

    public int threshold() {
        return threshold;
    }
}
