package com.example.claudedemo.agent.memory;

import com.example.claudedemo.llm.ChatMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 单会话短期消息内存.
 *
 * <p>以 {@link ConversationTurn} 为单位保存最近提问与回答,不含工具调用、schema、SQL 结果等
 * 中间状态。适用于 LLM 短期会话窗口。
 *
 * <p>裁剪策略：以 turn 为单位,当 turn 数超过 {@link #MAX_TURNS} 时裁掉最旧 turn。
 * 保证不会删除半轮对话(不会出现只有 question 没有 answer 的孤立消息)。
 *
 * <p><b>摘要压缩(V2 第四阶段)</b>:除 turns 外,还挂载一个 {@link SummaryMemory},
 * 由 {@link InMemoryConversationStore#appendTurn} 在 turn 数超阈值时调用
 * {@link MemoryCompressor} 生成。被淘汰的 turn 内容压缩进 summary,保留在 memory
 * 上的 turn 数量等于 {@code size() - keepRecent}。
 *
 * <p>线程安全：内部使用 {@code synchronized} 块保护 {@code turns} 列表与 summary 引用。
 *
 * @since 0.0.1
 */
public class ConversationMemory {

    /** 最大保存 turn 数量(V1 固定 20, FIFO 裁剪;V2 起作为压缩失败时的兜底). */
    public static final int MAX_TURNS = 20;

    private final String sessionId;
    private final List<ConversationTurn> turns;
    /** 会话级压缩摘要(可能为 {@link SummaryMemory#empty()}). */
    private SummaryMemory summary;

    /**
     * @param sessionId 会话 ID,不可为 null 或空
     */
    public ConversationMemory(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId 不能为空");
        }
        this.sessionId = sessionId;
        this.turns = new ArrayList<>();
        this.summary = SummaryMemory.empty();
    }

    /**
     * 追加一轮对话记录。
     *
     * <p>追加后若 turn 数超过 {@link #MAX_TURNS},自动裁掉最旧的 turn。
     */
    public void addTurn(ConversationTurn turn) {
        if (turn == null) {
            return;
        }
        synchronized (turns) {
            turns.add(turn);
            while (turns.size() > MAX_TURNS) {
                turns.remove(0);
            }
        }
    }

    /**
     * 返回当前所有 turn 的不可变副本。
     */
    public List<ConversationTurn> turns() {
        synchronized (turns) {
            return List.copyOf(turns);
        }
    }

    /**
     * 将保存的 turns 转换为 LLM 可消费的 {@link ChatMessage} 列表。
     *
     * <p>每个 turn 生成两条消息：user(question) → assistant(answer)。
     * 适合拼接到 LLM messages 的 system prompt 之后、当前 user question 之前。
     */
    public List<ChatMessage> toChatMessages() {
        synchronized (turns) {
            if (turns.isEmpty()) {
                return List.of();
            }
            List<ChatMessage> msgs = new ArrayList<>(turns.size() * 2);
            for (ConversationTurn turn : turns) {
                msgs.add(new ChatMessage("user", turn.question()));
                msgs.add(new ChatMessage("assistant", turn.answer()));
            }
            return Collections.unmodifiableList(msgs);
        }
    }

    /**
     * 当前 turn 数量。
     */
    public int size() {
        synchronized (turns) {
            return turns.size();
        }
    }

    /**
     * 是否无任何记录。
     */
    public boolean isEmpty() {
        synchronized (turns) {
            return turns.isEmpty();
        }
    }

    /**
     * 清空所有 turns（测试/调试用）。
     */
    public void clear() {
        synchronized (turns) {
            turns.clear();
        }
    }

    /**
     * 所属 sessionId。
     */
    public String sessionId() {
        return sessionId;
    }

    // ==================== 摘要压缩(V2 第四阶段) ====================

    /**
     * 获取当前会话摘要;未压缩时为 {@link SummaryMemory#empty()}。
     */
    public SummaryMemory summary() {
        synchronized (turns) {
            return summary;
        }
    }

    /**
     * 整体替换会话摘要(由 {@link InMemoryConversationStore} 在压缩后调用）。
     *
     * <p>传入 {@code null} 等价于 {@link SummaryMemory#empty()},用于清空摘要。
     */
    public void setSummary(SummaryMemory newSummary) {
        if (newSummary == null) {
            newSummary = SummaryMemory.empty();
        }
        synchronized (turns) {
            this.summary = newSummary;
        }
    }

    /**
     * 是否存在非空摘要。
     */
    public boolean hasSummary() {
        synchronized (turns) {
            return !summary.isEmpty();
        }
    }

    /**
     * 读取最近 {@code keepRecent} 条 turn 之上的"待淘汰"turn 副本(不影响原列表)。
     *
     * <p>若当前 turn 数 &lt;= keepRecent,返回空列表(无可淘汰内容)。
     *
     * @param keepRecent 保留的近期 turn 数
     * @return 不可变的待淘汰 turn 列表
     */
    public List<ConversationTurn> turnsToEvict(int keepRecent) {
        if (keepRecent < 0) {
            throw new IllegalArgumentException("keepRecent 必须 >= 0, 实际: " + keepRecent);
        }
        synchronized (turns) {
            int evictCount = turns.size() - keepRecent;
            if (evictCount <= 0) {
                return List.of();
            }
            return List.copyOf(turns.subList(0, evictCount));
        }
    }

    /**
     * 丢弃最旧的 {@code count} 条 turn;{@code count <= 0} 时为 no-op;超过当前 turn 数则全部清空。
     *
     * <p>与 {@link #addTurn(ConversationTurn)} 的 FIFO 裁剪不同,本方法主动按数量裁剪,
     * 由压缩流程在生成 summary 后调用。
     */
    public void dropOldest(int count) {
        if (count <= 0) {
            return;
        }
        synchronized (turns) {
            int actual = Math.min(count, turns.size());
            if (actual == 0) {
                return;
            }
            turns.subList(0, actual).clear();
        }
    }

    /**
     * 在 memory 自身锁内执行任意操作,保证 per-session 原子.
     *
     * <p>由 {@link InMemoryConversationStore#appendTurn} 等"check + 多次写"
     * 场景使用,避免外部在加锁后还需重复 {@code synchronized (turns)}。
     */
    public void executeUnderLock(Runnable action) {
        if (action == null) {
            return;
        }
        synchronized (turns) {
            action.run();
        }
    }
}
