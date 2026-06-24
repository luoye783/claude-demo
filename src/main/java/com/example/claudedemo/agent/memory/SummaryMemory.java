package com.example.claudedemo.agent.memory;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 会话级压缩摘要值对象.
 *
 * <p>由 {@link MemoryCompressor} 在 turn 数超过阈值时生成,挂载在
 * {@link ConversationMemory} 上,作为"近期 turns 之上的更早历史"的紧凑表示。
 *
 * <p><b>字段语义</b>
 * <ul>
 *   <li>{@link #summary}  — 自然语言摘要(200 字以内,LLM 生成)</li>
 *   <li>{@link #keyFacts} — 长期事实列表(实体/字段/数值/结论),3-8 条,LLM 生成</li>
 *   <li>{@link #version}  — 摘要版本号,从 1 开始自增,便于后续做增量比较 / 缓存失效</li>
 *   <li>{@link #updatedAtMs} — 本次摘要生成时间戳</li>
 * </ul>
 *
 * <p><b>不可变</b>:record 字段全部 final;更新时整体替换。
 *
 * <p><b>空表示</b>:调用 {@link #empty()} 获取的"无摘要"对象视为未压缩,所有
 * 字段取零值,可通过 {@link #isEmpty()} 判断。
 *
 * @since 0.0.1
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SummaryMemory(
        @JsonProperty("summary") String summary,
        @JsonProperty("keyFacts") List<String> keyFacts,
        @JsonProperty("version") long version,
        @JsonProperty("updatedAtMs") long updatedAtMs
) {

    /**
     * 紧凑构造器:防御性拷贝 keyFacts 防止外部修改,空入参视为空列表.
     */
    public SummaryMemory {
        keyFacts = (keyFacts == null) ? List.of() : List.copyOf(keyFacts);
    }

    /**
     * 便捷构造器:自动填入 {@code System.currentTimeMillis()}.
     */
    public SummaryMemory(String summary, List<String> keyFacts, long version) {
        this(summary, keyFacts, version, System.currentTimeMillis());
    }

    /**
     * 空摘要工厂.
     *
     * <p>等价于 {@code new SummaryMemory(null, List.of(), 0L, 0L)},{@link #isEmpty()} 为 true。
     */
    public static SummaryMemory empty() {
        return new SummaryMemory(null, List.of(), 0L, 0L);
    }

    /**
     * 是否未生成任何摘要.
     */
    public boolean isEmpty() {
        return (summary == null || summary.isBlank()) && keyFacts.isEmpty() && version == 0L;
    }
}
