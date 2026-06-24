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
 * <p>线程安全：内部使用 {@code synchronized} 块保护 {@code turns} 列表。
 *
 * @since 0.0.1
 */
public class ConversationMemory {

    /** 最大保存 turn 数量(V1 固定 20, FIFO 裁剪). */
    public static final int MAX_TURNS = 20;

    private final String sessionId;
    private final List<ConversationTurn> turns;

    /**
     * @param sessionId 会话 ID,不可为 null 或空
     */
    public ConversationMemory(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId 不能为空");
        }
        this.sessionId = sessionId;
        this.turns = new ArrayList<>();
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
}
