package com.example.claudedemo.agent.rag.hybrid;

import com.example.claudedemo.agent.rag.RagDocument;
import com.example.claudedemo.agent.rag.RagSearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RRF(Reciprocal Rank Fusion)结果融合器(V2 第十二阶段 RAG V7).
 *
 * <p>将关键词检索和向量检索两路结果融合为单一排序列表。
 *
 * <p><b>算法</b>:
 * <pre>
 *   rrfScore(id) = Σ 1.0 / (rrfK + rank_i)
 * </pre>
 * 其中 rank 从 1 开始。同一 id 在两路均出现时分数累加。
 *
 * <p><b>排序规则</b>:
 * <ol>
 *   <li>fusedScore 降序</li>
 *   <li>bestRank 升序(分数相同时 rank 更小的排前)</li>
 *   <li>id 升序(完全相同时稳定排序)</li>
 * </ol>
 *
 * @since 0.0.1
 */
public class RrfResultFusion {

    private static final Logger log = LoggerFactory.getLogger(RrfResultFusion.class);

    private final int rrfK;

    public RrfResultFusion(int rrfK) {
        this.rrfK = rrfK;
    }

    /**
     * 融合两路检索结果.
     *
     * @param keywordResults 关键词检索结果(按 rank 升序排列)
     * @param vectorResults  向量检索结果(按 rank 升序排列)
     * @param finalTopK      最终返回数上限
     * @return 融合后的 RagDocument 列表,按 fusedScore 降序
     */
    public List<RagDocument> fuse(List<RagSearchResult> keywordResults,
                                   List<RagSearchResult> vectorResults,
                                   int finalTopK) {
        if (finalTopK <= 0) return List.of();

        // id → 聚合信息
        Map<String, FusionEntry> map = new LinkedHashMap<>();

        // 关键词路径
        for (RagSearchResult r : nullSafe(keywordResults)) {
            double rrf = 1.0 / (rrfK + r.rank());
            FusionEntry entry = map.computeIfAbsent(r.id(), FusionEntry::new);
            entry.addKeyword(r, rrf);
        }

        // 向量路径
        for (RagSearchResult r : nullSafe(vectorResults)) {
            double rrf = 1.0 / (rrfK + r.rank());
            FusionEntry entry = map.computeIfAbsent(r.id(), FusionEntry::new);
            entry.addVector(r, rrf);
        }

        if (map.isEmpty()) return List.of();

        // 排序: fusedScore desc → bestRank asc → id asc
        List<FusionEntry> sorted = new ArrayList<>(map.values());
        sorted.sort(Comparator
                .comparingDouble(FusionEntry::fusedScore).reversed()
                .thenComparingInt(FusionEntry::bestRank)
                .thenComparing(FusionEntry::id));

        // 截断 + 转换
        List<RagDocument> result = new ArrayList<>();
        for (int i = 0; i < Math.min(finalTopK, sorted.size()); i++) {
            result.add(sorted.get(i).toRagDocument());
        }
        return List.copyOf(result);
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list == null ? List.of() : list;
    }

    /**
     * 单个 id 的融合条目,聚合两路信息.
     */
    private static class FusionEntry {
        private final String id;
        private double fusedScore;
        private int bestRank = Integer.MAX_VALUE;
        private RagSearchResult bestResult; // rank 最小的结果

        FusionEntry(String id) {
            this.id = id;
        }

        void addKeyword(RagSearchResult r, double rrf) {
            fusedScore += rrf;
            if (r.rank() < bestRank) {
                bestRank = r.rank();
                bestResult = r;
            }
        }

        void addVector(RagSearchResult r, double rrf) {
            fusedScore += rrf;
            if (r.rank() < bestRank) {
                bestRank = r.rank();
                bestResult = r;
            }
        }

        String id() { return id; }
        double fusedScore() { return fusedScore; }
        int bestRank() { return bestRank; }

        RagDocument toRagDocument() {
            if (bestResult == null) return null;

            // 构建聚合 metadata
            Map<String, Object> meta = new LinkedHashMap<>();
            if (bestResult.metadataView() != null) {
                meta.putAll(bestResult.metadataView());
            }
            meta.put("retrievalType", "HYBRID");
            meta.put("fusedScore", fusedScore);

            double clamped = Math.min(1.0, Math.max(0.0, fusedScore));
            return new RagDocument(
                    bestResult.id(), bestResult.title(), bestResult.content(),
                    bestResult.source(), clamped, List.of(), meta);
        }
    }
}
