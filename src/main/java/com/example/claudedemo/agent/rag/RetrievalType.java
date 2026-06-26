package com.example.claudedemo.agent.rag;

/**
 * 检索路径类型(V2 第十二阶段 RAG V7).
 *
 * <p>标记 {@link RagSearchResult} 的来源路径,与 {@link RetrievalMode} 区分:
 * <ul>
 *   <li>{@link RetrievalMode} — 配置选择(用哪种检索策略)</li>
 *   <li>{@link RetrievalType} — 结果标签(该结果来自哪条路径)</li>
 * </ul>
 *
 * @since 0.0.1
 */
public enum RetrievalType {
    KEYWORD,
    VECTOR,
    HYBRID
}
