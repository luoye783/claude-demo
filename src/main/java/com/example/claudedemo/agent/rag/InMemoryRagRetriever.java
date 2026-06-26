package com.example.claudedemo.agent.rag;

import com.example.claudedemo.agent.rag.chunker.SimpleTextChunker;
import com.example.claudedemo.agent.rag.embedding.EmbeddingClient;
import com.example.claudedemo.agent.rag.embedding.EmbeddingVector;
import com.example.claudedemo.agent.rag.hybrid.HybridProperties;
import com.example.claudedemo.agent.rag.hybrid.RrfResultFusion;
import com.example.claudedemo.agent.rag.loader.MarkdownKnowledgeDocumentLoader;
import com.example.claudedemo.agent.rag.store.VectorDocument;
import com.example.claudedemo.agent.rag.store.VectorSearchResult;
import com.example.claudedemo.agent.rag.store.VectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 内存版 RAG 检索器(V2 第十二阶段 RAG V7).
 *
 * <p><b>三检索路径</b>:
 * <ul>
 *   <li><b>关键词路径</b>:将 question 与每篇文档/chunk 的 {@code title + content} 做 token 集合相交,
 *       score = {@code 命中数 / max(qSize, docSize, 1)}</li>
 *   <li><b>向量路径</b>(V3):通过 {@link EmbeddingClient} 将 question 转为向量,
 *       经 {@link VectorStore#search} 返回余弦相似度结果</li>
 *   <li><b>混合路径</b>(V7):双路检索 + RRF 融合,取长补短</li>
 * </ul>
 *
 * <p><b>构造器</b>:
 * <ul>
 *   <li>{@link #InMemoryRagRetriever()} / {@link #InMemoryRagRetriever(List)} —— V1 兼容</li>
 *   <li>{@link #InMemoryRagRetriever(KnowledgeDocumentLoader, TextChunker, RagProperties)} —— V2 生产</li>
 *   <li>{@link #InMemoryRagRetriever(KnowledgeDocumentLoader, TextChunker, RagProperties,
 *       EmbeddingClient, VectorStore)} —— V3 完整</li>
 *   <li>{@link #InMemoryRagRetriever(KnowledgeDocumentLoader, TextChunker, RagProperties,
 *       EmbeddingClient, VectorStore, HybridProperties)} —— V7 hybrid</li>
 * </ul>
 *
 * @since 0.0.1
 */
public class InMemoryRagRetriever implements RagRetriever {

    private static final Logger log = LoggerFactory.getLogger(InMemoryRagRetriever.class);

    /** 抽取 ASCII 单词/数字/下划线 或 单个中文字符 作为 token. */
    private static final Pattern TOKEN_PATTERN =
            Pattern.compile("[a-z0-9_]+|[\\u4e00-\\u9fa5]");

    /** 检索条目列表(内部记录,预计算 token 集). */
    private final List<RetrievalEntry> entries;

    /** 检索模式(KEYWORD / VECTOR / HYBRID). */
    private final RetrievalMode retrievalMode;
    /** Embedding 客户端(V3 可选,仅 vector/hybrid 模式使用). */
    private final EmbeddingClient embeddingClient;
    /** 向量存储(V3 可选,仅 vector/hybrid 模式使用). */
    private final VectorStore vectorStore;
    /** Hybrid 配置(V7 可选). */
    private final HybridProperties hybridProps;
    /** RRF 融合器(V7 可选). */
    private final RrfResultFusion rrfFusion;

    // ==================== V1 构造器(兼容) ====================

    /**
     * 空语料构造器:内部无任何检索条目,retrieve 始终返回空.
     */
    public InMemoryRagRetriever() {
        this.entries = List.of();
        this.retrievalMode = RetrievalMode.KEYWORD;
        this.embeddingClient = null;
        this.vectorStore = null;
        this.hybridProps = null;
        this.rrfFusion = null;
    }

    /**
     * V1 兼容构造器:接受预构造的 {@link RagDocument} 列表.
     *
     * @param documents RagDocument 列表;null / 空视为空语料
     */
    public InMemoryRagRetriever(List<RagDocument> documents) {
        this.entries = (documents == null || documents.isEmpty())
                ? List.of()
                : documents.stream().map(this::asEntry).toList();
        this.retrievalMode = RetrievalMode.KEYWORD;
        this.embeddingClient = null;
        this.vectorStore = null;
        this.hybridProps = null;
        this.rrfFusion = null;
    }

    // ==================== V2 生产构造器(仅有关键词) ====================

    /**
     * V2 生产构造器:从知识库加载文档 → 切分 chunk → 建立索引.
     */
    public InMemoryRagRetriever(KnowledgeDocumentLoader loader,
                                 TextChunker chunker,
                                 RagProperties props) {
        this(loader, chunker, props, null, null, null);
    }

    // ==================== V3 完整生产构造器 ====================

    /**
     * V3 完整构造器:关键词 + 向量双路径.
     */
    public InMemoryRagRetriever(KnowledgeDocumentLoader loader,
                                 TextChunker chunker,
                                 RagProperties props,
                                 EmbeddingClient embeddingClient,
                                 VectorStore vectorStore) {
        this(loader, chunker, props, embeddingClient, vectorStore, null);
    }

    // ==================== V7 完整构造器(Hybrid) ====================

    /**
     * V7 完整构造器:关键词 + 向量 + Hybrid 三路径.
     *
     * @param loader          文档加载器
     * @param chunker         文本切分器
     * @param props           配置(含 retrievalMode)
     * @param embeddingClient Embedding 客户端
     * @param vectorStore     向量存储
     * @param hybridProps     Hybrid 配置;hybrid 模式必填
     */
    public InMemoryRagRetriever(KnowledgeDocumentLoader loader,
                                 TextChunker chunker,
                                 RagProperties props,
                                 EmbeddingClient embeddingClient,
                                 VectorStore vectorStore,
                                 HybridProperties hybridProps) {
        this.retrievalMode = (props == null) ? RetrievalMode.KEYWORD : props.getRetrievalMode();
        this.embeddingClient = embeddingClient;
        this.vectorStore = vectorStore;
        this.hybridProps = hybridProps;
        this.rrfFusion = hybridProps != null ? new RrfResultFusion(hybridProps.getRrfK()) : null;

        // 关键词路径: loader → chunker → tokenize (always)
        List<RetrievalEntry> tmp = new ArrayList<>();
        if (loader != null && chunker != null) {
            try {
                List<KnowledgeDocument> docs = loader.load();
                if (docs != null && !docs.isEmpty()) {
                    for (KnowledgeDocument doc : docs) {
                        try {
                            for (KnowledgeChunk c : chunker.chunk(doc)) {
                                tmp.add(asEntryFromChunk(c));
                            }
                        } catch (Exception e) {
                            log.warn("跳过文档 {}: {}", doc.id(), e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("文档加载失败: {}", e.getMessage());
            }
        }
        this.entries = List.copyOf(tmp);
        log.info("关键词索引: {} 个条目", this.entries.size());

        // 向量路径: embed → vectorStore.upsert (vector / hybrid 模式)
        boolean needVector = retrievalMode == RetrievalMode.VECTOR
                || retrievalMode == RetrievalMode.HYBRID;
        if (needVector) {
            if (embeddingClient == null || vectorStore == null) {
                log.warn("{} 模式但 embeddingClient/vectorStore 为 null,退化到关键词", retrievalMode);
            } else {
                int vecCount = 0;
                for (KnowledgeDocument doc : (tryLoad(loader))) {
                    try {
                        for (KnowledgeChunk c : chunker.chunk(doc)) {
                            EmbeddingVector vec = embeddingClient.embed(c.content());
                            vectorStore.upsert(new VectorDocument(
                                    c.id(), c.documentId(), c.content(), c.source(),
                                    null, c.metadataView(), vec));
                            vecCount++;
                        }
                    } catch (Exception e) {
                        log.warn("向量化跳过 {}: {}", doc.id(), e.getMessage());
                    }
                }
                log.info("向量索引: {} 个文档, {} 个向量",
                        tryLoad(loader).size(), vecCount);
            }
        }

        if (retrievalMode == RetrievalMode.HYBRID && hybridProps != null) {
            log.info("Hybrid 检索: kwTopK={}, vecTopK={}, finalTopK={}, rrfK={}",
                    hybridProps.getKeywordTopK(), hybridProps.getVectorTopK(),
                    hybridProps.getFinalTopK(), hybridProps.getRrfK());
        }
    }

    /**
     * 安全加载文档列表,失败返回空列表.
     */
    private static List<KnowledgeDocument> tryLoad(KnowledgeDocumentLoader loader) {
        if (loader == null) return List.of();
        try { return loader.load() != null ? loader.load() : List.of(); }
        catch (Exception e) { return List.of(); }
    }

    // ==================== RagRetriever 接口 ====================

    @Override
    public List<RagDocument> retrieve(String question, int topK) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("question 不能为空");
        }
        if (topK <= 0) {
            throw new IllegalArgumentException("topK 必须 > 0, 实际: " + topK);
        }
        return switch (retrievalMode) {
            case VECTOR  -> vectorRetrieve(question, topK);
            case KEYWORD -> keywordRetrieve(question, topK);
            case HYBRID  -> hybridRetrieve(question, topK);
        };
    }

    // ==================== 关键词检索 ====================

    /**
     * 关键词检索:返回 {@link RagSearchResult} 列表(供 hybrid 融合使用).
     */
    private List<RagSearchResult> keywordSearch(String question, int topK) {
        Set<String> qTokens = tokenize(question);
        if (qTokens.isEmpty()) return List.of();

        List<RagSearchResult> scored = new ArrayList<>();
        for (RetrievalEntry entry : entries) {
            int hits = countIntersection(qTokens, entry.tokens);
            if (hits == 0) continue;
            double score = (double) hits / Math.max(Math.max(qTokens.size(), entry.tokens.size()), 1);
            scored.add(new RagSearchResult(
                    entry.id, extractDocumentId(entry.id), entry.title,
                    entry.content, entry.source, score, 0,
                    RetrievalType.KEYWORD, entry.metadata));
        }
        scored.sort(Comparator.comparingDouble(RagSearchResult::score).reversed());
        if (scored.size() > topK) scored = scored.subList(0, topK);

        // 设 rank(1-based)
        List<RagSearchResult> ranked = new ArrayList<>();
        for (int i = 0; i < scored.size(); i++) {
            RagSearchResult r = scored.get(i);
            ranked.add(new RagSearchResult(
                    r.id(), r.documentId(), r.title(), r.content(), r.source(),
                    r.score(), i + 1, RetrievalType.KEYWORD, r.metadataView()));
        }
        return List.copyOf(ranked);
    }

    /**
     * 关键词检索 → RagDocument(与 V2 行为完全一致).
     */
    private List<RagDocument> keywordRetrieve(String question, int topK) {
        List<RagSearchResult> results = keywordSearch(question, topK);
        return results.stream()
                .map(r -> {
                    Map<String, Object> meta = new java.util.LinkedHashMap<>(r.metadataView());
                    meta.put("retrievalType", "KEYWORD");
                    return new RagDocument(r.id(), r.title(), r.content(), r.source(),
                            r.score(), List.of(), meta);
                })
                .toList();
    }

    // ==================== 向量检索 ====================

    /**
     * 向量检索:返回 {@link RagSearchResult} 列表(供 hybrid 融合使用).
     */
    private List<RagSearchResult> vectorSearch(String question, int topK) {
        if (embeddingClient == null || vectorStore == null) {
            return List.of();
        }
        EmbeddingVector queryVec = embeddingClient.embed(question);
        List<VectorSearchResult> results = vectorStore.search(queryVec, topK);

        List<RagSearchResult> out = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            VectorSearchResult r = results.get(i);
            if (r.score() <= 0.0) continue;
            VectorDocument doc = r.document();
            if (doc == null) continue;
            out.add(new RagSearchResult(
                    doc.id(), doc.documentId(),
                    doc.source(), // title ← source
                    doc.content(), doc.source(),
                    r.score(), i + 1, RetrievalType.VECTOR,
                    doc.metadataView()));
        }
        return List.copyOf(out);
    }

    /**
     * 向量检索 → RagDocument(与 V3 行为完全一致).
     */
    private List<RagDocument> vectorRetrieve(String question, int topK) {
        if (embeddingClient == null || vectorStore == null) {
            log.warn("vector 模式但 embeddingClient/vectorStore 不可用,返回空");
            return List.of();
        }
        EmbeddingVector queryVec = embeddingClient.embed(question);
        List<VectorSearchResult> results = vectorStore.search(queryVec, topK);
        return results.stream()
                .filter(r -> r.score() > 0.0)
                .map(r -> {
                    VectorDocument doc = r.document();
                    if (doc == null) return null;
                    double score = Math.min(1.0, Math.max(0.0, r.score()));
                    Map<String, Object> meta = new java.util.LinkedHashMap<>(doc.metadataView());
                    meta.put("retrievalType", "VECTOR");
                    return new RagDocument(doc.id(), doc.source(), doc.content(),
                            doc.source(), score, List.of(), meta);
                })
                .filter(Objects::nonNull)
                .toList();
    }

    // ==================== Hybrid 检索(V7 新增) ====================

    /**
     * Hybrid 检索:关键词 + 向量双路 → RRF 融合.
     *
     * @param question 用户问题
     * @param topK     入参 topK,> 0 时覆盖配置的 finalTopK
     */
    private List<RagDocument> hybridRetrieve(String question, int topK) {
        if (hybridProps == null || rrfFusion == null) {
            log.warn("HYBRID 模式但 hybridProps 为 null,降级到关键词");
            return keywordRetrieve(question, topK);
        }

        int finalTopK = topK > 0 ? topK : hybridProps.getFinalTopK();

        // 双路检索
        List<RagSearchResult> kwResults = keywordSearch(question, hybridProps.getKeywordTopK());
        List<RagSearchResult> vecResults = vectorSearch(question, hybridProps.getVectorTopK());

        log.debug("hybridRetrieve: kw={}, vec={}, finalTopK={}",
                kwResults.size(), vecResults.size(), finalTopK);

        // RRF 融合
        return rrfFusion.fuse(kwResults, vecResults, finalTopK);
    }

    // ==================== 内部结构 ====================

    /**
     * 检索条目 —— 包装单个 chunk/doc 及其预计算的 token 集合.
     */
    private record RetrievalEntry(
            String id, String title, String content, String source,
            Set<String> tokens, Map<String, Object> metadata
    ) {}

    private RetrievalEntry asEntry(RagDocument doc) {
        Set<String> tokens = tokenize(doc.title() + " " + doc.content()
                + " " + String.join(" ", doc.keywords()));
        Map<String, Object> meta = (doc.metadataView() == null || doc.metadataView().isEmpty())
                ? java.util.Map.of() : doc.metadataView();
        return new RetrievalEntry(doc.id(), doc.title(), doc.content(),
                doc.source(), tokens, meta);
    }

    private RetrievalEntry asEntryFromChunk(KnowledgeChunk chunk) {
        Set<String> tokens = tokenize(chunk.title() + " " + chunk.content());
        Map<String, Object> meta = (chunk.metadataView() == null || chunk.metadataView().isEmpty())
                ? java.util.Map.of() : chunk.metadataView();
        return new RetrievalEntry(chunk.id(), chunk.title(), chunk.content(),
                chunk.source(), tokens, meta);
    }

    // ==================== Tokenize ====================

    private static Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) return Set.of();
        Set<String> out = new LinkedHashSet<>();
        Matcher m = TOKEN_PATTERN.matcher(text.toLowerCase(Locale.ROOT));
        while (m.find()) {
            out.add(m.group());
        }
        return out;
    }

    private static int countIntersection(Set<String> a, Set<String> b) {
        int n = 0;
        for (String s : a) {
            if (b.contains(s)) n++;
        }
        return n;
    }

    /** 从 chunk id 提取 documentId(与 VectorDocument.extractDocumentId 逻辑一致). */
    private static String extractDocumentId(String id) {
        if (id == null) return null;
        int idx = id.lastIndexOf("-chunk-");
        return idx > 0 ? id.substring(0, idx) : id;
    }

    // ==================== V1 兼容(保留但不建议使用) ====================

    /**
     * V1 内置种子文档列表.
     *
     * @deprecated V2 改为从 knowledge-base 目录加载.
     */
    @Deprecated
    public static List<RagDocument> defaultDocuments() {
        return List.of(
                new RagDocument("doc-users-table",
                        "users 表说明",
                        "users 表存储用户基础信息,主键 id,包含 city、status、is_deleted 等字段。",
                        "knowledge-base/users.md",
                        List.of("users", "用户", "表")),
                new RagDocument("doc-city-field",
                        "city 字段说明",
                        "city 字段表示用户所在城市,字符串类型,可用于按城市分组统计。",
                        "knowledge-base/users.md",
                        List.of("city", "城市", "字段")),
                new RagDocument("doc-is-deleted",
                        "is_deleted 软删除标记",
                        "is_deleted = 0 表示未删除(有效记录),is_deleted = 1 表示已删除。",
                        "knowledge-base/soft-delete.md",
                        List.of("is_deleted", "删除", "软删除", "deleted")),
                new RagDocument("doc-status",
                        "status 字段说明",
                        "status = 1 表示有效用户,status = 0 表示禁用/未激活用户。",
                        "knowledge-base/users.md",
                        List.of("status", "状态", "有效", "激活")),
                new RagDocument("doc-execute-sql",
                        "execute_sql 工具说明",
                        "execute_sql 只能执行只读 SELECT,会自动注入 LIMIT 1000 防止全表扫。",
                        "knowledge-base/tools.md",
                        List.of("execute_sql", "select", "limit", "工具", "只读"))
        );
    }

    /**
     * 当前 entry 数量(测试/调试用).
     */
    public int entryCount() {
        return entries.size();
    }
}
