package com.example.claudedemo.agent.rag;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * RAG 检索中间结果(V2 第十二阶段 RAG V7).
 *
 * <p>供 RRF 融合阶段使用,介于原始检索结果和最终 {@link RagDocument} 之间。
 *
 * <p><b>与 RagDocument 的区别</b>:
 * <ul>
 *   <li>{@link RagDocument} — Agent 消费的最终产出</li>
 *   <li>{@link RagSearchResult} — 融合过程的中间载体,携带 rank 和 retrievalType</li>
 * </ul>
 *
 * @param id             chunk 唯一 ID
 * @param documentId     所属文档 ID
 * @param title          文档标题
 * @param content        文本内容
 * @param source         来源路径
 * @param score          原始检索分数
 * @param rank           在原始结果列表中的排位(1-based)
 * @param retrievalType  检索路径
 * @param metadata       扩展元信息
 * @since 0.0.1
 */
public record RagSearchResult(
        String id,
        String documentId,
        String title,
        String content,
        String source,
        double score,
        int rank,
        RetrievalType retrievalType,
        Map<String, Object> metadata
) {

    public RagSearchResult {
        metadata = (metadata == null) ? Map.of() : Map.copyOf(metadata);
        if (rank < 1) rank = 1;
    }

    public Map<String, Object> metadataView() {
        return Collections.unmodifiableMap(metadata);
    }

    /**
     * 转为最终 RagDocument.
     *
     * @param fusedScore 融合后的分数
     * @return RagDocument,score 替换为 fusedScore,metadata 补充检索信息
     */
    public RagDocument toRagDocument(double fusedScore) {
        Map<String, Object> enrichedMeta = new java.util.LinkedHashMap<>(metadata);
        enrichedMeta.put("retrievalType", retrievalType.name());
        enrichedMeta.put("fusedScore", fusedScore);
        enrichedMeta.put("originalScore", score);
        enrichedMeta.put("rank", rank);

        double clamped = Math.min(1.0, Math.max(0.0, fusedScore));
        return new RagDocument(id, title, content, source, clamped,
                List.of(), enrichedMeta);
    }
}
