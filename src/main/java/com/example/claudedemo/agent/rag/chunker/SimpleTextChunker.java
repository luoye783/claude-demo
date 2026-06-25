package com.example.claudedemo.agent.rag.chunker;

import com.example.claudedemo.agent.rag.KnowledgeChunk;
import com.example.claudedemo.agent.rag.KnowledgeDocument;
import com.example.claudedemo.agent.rag.TextChunker;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 基于字符长度的简单切分器(V2 第七阶段 RAG V2).
 *
 * <p><b>规则</b>:
 * <ul>
 *   <li>按 {@code chunkSize} 字符长度截断,不做单词边界 / Markdown AST 分析</li>
 *   <li>相邻 chunk 之间保留 {@code overlap} 字符的回退</li>
 *   <li>文档长度 &le; chunkSize 时产出 1 个 chunk(不截断)</li>
 *   <li>空文档 → 空列表</li>
 * </ul>
 *
 * <p>chunk id 格式: {@code {documentId}-chunk-{index}}
 *
 * @since 0.0.1
 */
public class SimpleTextChunker implements TextChunker {

    private final int chunkSize;
    private final int overlap;

    /**
     * @param chunkSize  每 chunk 字符数,必须 &gt; 0
     * @param overlap    相邻 chunk 的重叠字符数,必须 &gt;= 0
     */
    public SimpleTextChunker(int chunkSize, int overlap) {
        if (chunkSize <= 0) throw new IllegalArgumentException("chunkSize 必须 > 0, 实际: " + chunkSize);
        if (overlap < 0) throw new IllegalArgumentException("overlap 必须 >= 0, 实际: " + overlap);
        if (overlap >= chunkSize) {
            overlap = chunkSize / 2;
        }
        this.chunkSize = chunkSize;
        this.overlap = overlap;
    }

    @Override
    public List<KnowledgeChunk> chunk(KnowledgeDocument document) {
        if (document == null) return List.of();
        String content = document.content();
        if (content == null || content.isBlank()) return List.of();

        int len = content.length();
        if (len <= chunkSize) {
            return List.of(buildChunk(document, content, 0));
        }

        List<KnowledgeChunk> result = new ArrayList<>();
        int start = 0;
        int idx = 0;
        while (start < len) {
            int end = Math.min(start + chunkSize, len);
            String chunkText = content.substring(start, end);
            result.add(buildChunk(document, chunkText, idx));
            idx++;
            start = end - overlap;
            if (start >= len) break;
        }
        return List.copyOf(result);
    }

    private KnowledgeChunk buildChunk(KnowledgeDocument doc, String chunkText, int index) {
        return new KnowledgeChunk(
                doc.id() + "-chunk-" + index,
                doc.id(),
                doc.title(),
                chunkText,
                doc.source(),
                index,
                Map.of("chunkSize", chunkSize, "overlap", overlap)
        );
    }
}
