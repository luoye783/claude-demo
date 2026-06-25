package com.example.claudedemo.agent.rag;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 内存版 RAG 检索器(V2 第六阶段 RAG V1).
 *
 * <p><b>检索策略</b>:简单关键词匹配 ——
 * <ol>
 *   <li>将 question 与每篇 {@link RagDocument} 的 {@link RagDocument#keywords()} 做集合相交</li>
 *   <li>score = {@code 命中关键词数 / max(question关键词数, doc关键词数, 1)}</li>
 *   <li>过滤 score &gt; 0 的文档,按 score 降序排序</li>
 *   <li>截断到前 topK 条</li>
 *   <li>同一文档 id 多次命中时只返回一次(由 RagDocument 本身唯一)</li>
 * </ol>
 *
 * <p><b>分词策略</b>:V1 极简 —— 按非字母数字下划线字符切分,小写化;
 * <b>不做中文分词</b>(中文字符串会作为单字 token 出现在集合中)。
 * 目标:打通 RAG 插槽,不追求检索质量;V2 换 Embedding/向量检索。
 *
 * <p><b>种子文档</b>:通过 {@link #defaultDocuments()} 静态方法返回,便于 V2 替换为
 * 文件 / 数据库 / 向量库来源;Spring 注入时使用无参构造器 + 默认文档列表。
 *
 * @since 0.0.1
 */
@Component
public class InMemoryRagRetriever implements RagRetriever {

    /** 抽取 ASCII 单词/数字/下划线 或 单个中文字符 作为 token. */
    private static final Pattern TOKEN_PATTERN =
            Pattern.compile("[a-z0-9_]+|[\\u4e00-\\u9fa5]");

    private final List<RagDocument> documents;

    /**
     * Spring 注入用:加载默认种子文档.
     */
    public InMemoryRagRetriever() {
        this(defaultDocuments());
    }

    /**
     * 自定义文档列表(便于测试 / V2 替换数据源).
     */
    public InMemoryRagRetriever(List<RagDocument> documents) {
        this.documents = (documents == null) ? List.of() : List.copyOf(documents);
    }

    @Override
    public List<RagDocument> retrieve(String question, int topK) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("question 不能为空");
        }
        if (topK <= 0) {
            throw new IllegalArgumentException("topK 必须 > 0, 实际: " + topK);
        }
        Set<String> qTokens = tokenize(question);
        if (qTokens.isEmpty()) {
            return List.of();
        }

        List<RagDocument> scored = new ArrayList<>();
        for (RagDocument doc : documents) {
            Set<String> docTokens = tokenize(String.join(" ",
                    doc.title() + " " + doc.content() + " " + String.join(" ", doc.keywords())));
            int hits = countIntersection(qTokens, docTokens);
            if (hits == 0) {
                continue;
            }
            double score = (double) hits / Math.max(Math.max(qTokens.size(), docTokens.size()), 1);
            scored.add(new RagDocument(
                    doc.id(), doc.title(), doc.content(), doc.source(),
                    score, doc.keywords(), doc.metadataView()));
        }
        scored.sort(Comparator.comparingDouble(RagDocument::score).reversed());
        if (scored.size() > topK) {
            scored = scored.subList(0, topK);
        }
        return List.copyOf(scored);
    }

    /**
     * 简单分词:ASCII 单词/数字/下划线保留为整体,中文字符拆为单字 token.
     *
     * <p>例:
     * <ul>
     *   <li>"users 表" → {"users", "表"}</li>
     *   <li>"用户城市" → {"用", "户", "城", "市"}</li>
     *   <li>"hello world" → {"hello", "world"}</li>
     * </ul>
     */
    private static Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
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

    /**
     * V1 内置种子文档列表 —— 用于演示与打通链路.
     *
     * <p>显式抽出为静态方法,便于 V2 替换为文件 / 数据库 / 向量库来源,而无需修改本类逻辑。
     */
    public static List<RagDocument> defaultDocuments() {
        return List.of(
                new RagDocument(
                        "doc-users-table",
                        "users 表说明",
                        "users 表存储用户基础信息,主键 id,包含 city、status、is_deleted 等字段。",
                        "knowledge-base/users.md",
                        List.of("users", "用户", "表")),
                new RagDocument(
                        "doc-city-field",
                        "city 字段说明",
                        "city 字段表示用户所在城市,字符串类型,可用于按城市分组统计。",
                        "knowledge-base/users.md",
                        List.of("city", "城市", "字段")),
                new RagDocument(
                        "doc-is-deleted",
                        "is_deleted 软删除标记",
                        "is_deleted = 0 表示未删除(有效记录),is_deleted = 1 表示已删除。",
                        "knowledge-base/soft-delete.md",
                        List.of("is_deleted", "删除", "软删除", "deleted")),
                new RagDocument(
                        "doc-status",
                        "status 字段说明",
                        "status = 1 表示有效用户,status = 0 表示禁用/未激活用户。",
                        "knowledge-base/users.md",
                        List.of("status", "状态", "有效", "激活")),
                new RagDocument(
                        "doc-execute-sql",
                        "execute_sql 工具说明",
                        "execute_sql 只能执行只读 SELECT,会自动注入 LIMIT 1000 防止全表扫。",
                        "knowledge-base/tools.md",
                        List.of("execute_sql", "select", "limit", "工具", "只读"))
        );
    }
}
