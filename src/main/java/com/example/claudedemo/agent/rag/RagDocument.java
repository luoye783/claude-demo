package com.example.claudedemo.agent.rag;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * RAG 检索结果单元(V2 第六阶段 RAG V1).
 *
 * <p>由 {@link RagRetriever} 返回的最小知识载体;被 Agent 渲染为 LLM system 消息
 * 注入到 messages 列表中,作为业务知识背景。
 *
 * <p><b>字段语义</b>:
 * <ul>
 *   <li>{@link #id} — 文档唯一 ID,便于去重与日志追踪</li>
 *   <li>{@link #title} — 文档标题,用于 LLM 区分与 markdown 渲染</li>
 *   <li>{@link #content} — 文档主体,会被注入到 system 消息的 content 字段</li>
 *   <li>{@link #source} — 来源标记(如文件路径 / 数据库表名),便于审计</li>
 *   <li>{@link #score} — 检索相关度(0.0~1.0);{@link InMemoryRagRetriever} 用
 *       {@code 命中关键词数 / 关键词总数} 近似,V2 可换 cosine 相似度</li>
 *   <li>{@link #keywords} — 文档关键词列表,V1 关键词匹配的对照集合</li>
 *   <li>{@link #metadata} — 扩展字段(category / version / tags 等),不可变</li>
 * </ul>
 *
 * <p><b>不可变</b>:record 字段全部 final;构造时做防御性拷贝。
 *
 * @since 0.0.1
 */
public record RagDocument(
        String id,
        String title,
        String content,
        String source,
        double score,
        List<String> keywords,
        Map<String, Object> metadata
) {

    /**
     * 紧凑构造器:keywords / metadata 防御性拷贝,空入参视为空集合.
     */
    public RagDocument {
        keywords = (keywords == null) ? List.of() : List.copyOf(keywords);
        metadata = (metadata == null) ? Map.of() : Map.copyOf(metadata);
        if (score < 0.0) score = 0.0;
        if (score > 1.0) score = 1.0;
    }

    /**
     * 便捷构造器:无 keywords,无 metadata,score=0(由检索器填充).
     */
    public RagDocument(String id, String title, String content, String source) {
        this(id, title, content, source, 0.0, List.of(), Map.of());
    }

    /**
     * 便捷构造器:带 keywords,无 metadata,score=0(由检索器填充).
     */
    public RagDocument(String id, String title, String content, String source, List<String> keywords) {
        this(id, title, content, source, 0.0, keywords, Map.of());
    }

    /**
     * 不可变 metadata 视图.
     */
    public Map<String, Object> metadataView() {
        return Collections.unmodifiableMap(metadata);
    }
}
