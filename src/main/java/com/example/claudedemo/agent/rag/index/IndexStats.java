package com.example.claudedemo.agent.rag.index;

/**
 * 索引操作统计(V2 第十一阶段 RAG V6).
 *
 * <p>由 {@link KnowledgeIndexService} 的各个方法返回,
 * 记录本次操作的处理量与耗时。
 *
 * @param documentCount    知识库文档总数
 * @param chunkCount       切分出的 chunk 总数
 * @param indexedChunkCount 新写入/更新的 chunk 数
 * @param skippedChunkCount 未变化的 chunk 数(跳过 embedding)
 * @param deletedChunkCount 被清理的 chunk 数
 * @param durationMs        本次操作耗时(毫秒)
 * @since 0.0.1
 */
public record IndexStats(
        int documentCount,
        int chunkCount,
        int indexedChunkCount,
        int skippedChunkCount,
        int deletedChunkCount,
        long durationMs
) {

    /** 无操作的空统计. */
    public static IndexStats empty() {
        return new IndexStats(0, 0, 0, 0, 0, 0);
    }

    @Override
    public String toString() {
        return "IndexStats{"
                + "documents=" + documentCount
                + ", chunks=" + chunkCount
                + ", indexed=" + indexedChunkCount
                + ", skipped=" + skippedChunkCount
                + ", deleted=" + deletedChunkCount
                + ", duration=" + durationMs + "ms"
                + '}';
    }
}
