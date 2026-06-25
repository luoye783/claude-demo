package com.example.claudedemo.agent.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RAG 配置(V2 第八阶段 RAG V3).
 *
 * <p>对应 application.yml 中的 {@code rag} 配置块。
 *
 * <p><b>V3 新增</b>:{@link #retrievalMode} / {@link #embeddingDimension}
 *
 * @since 0.0.1
 */
@ConfigurationProperties(prefix = "rag")
public class RagProperties {

    /** 检索模式(keyword / vector),默认 keyword. */
    private RetrievalMode retrievalMode = RetrievalMode.KEYWORD;

    /** 知识库路径(普通相对/绝对路径 / classpath: / file:). */
    private String knowledgeBasePath = "knowledge-base";

    /** 切分字符数(SimpleTextChunker 默认值). */
    private int chunkSize = 800;

    /** 相邻 chunk 重叠字符数. */
    private int chunkOverlap = 100;

    /** 向量维度(SimpleHashEmbeddingClient 用). */
    private int embeddingDimension = 128;

    /** 检索返回文档数上限. */
    private int topK = 3;

    /** 命中阈值,score < min-score 视为不命中. */
    private double minScore = 0.05;

    /** 注入 system 消息的 RAG 总字符数上限,防止挤占 turns 空间. */
    private int maxContentChars = 1500;

    public RetrievalMode getRetrievalMode() {
        return retrievalMode;
    }

    public void setRetrievalMode(RetrievalMode retrievalMode) {
        this.retrievalMode = retrievalMode;
    }

    public String getKnowledgeBasePath() {
        return knowledgeBasePath;
    }

    public void setKnowledgeBasePath(String knowledgeBasePath) {
        this.knowledgeBasePath = knowledgeBasePath;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    public int getChunkOverlap() {
        return chunkOverlap;
    }

    public void setChunkOverlap(int chunkOverlap) {
        this.chunkOverlap = chunkOverlap;
    }

    public int getEmbeddingDimension() {
        return embeddingDimension;
    }

    public void setEmbeddingDimension(int embeddingDimension) {
        this.embeddingDimension = embeddingDimension;
    }

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public double getMinScore() {
        return minScore;
    }

    public void setMinScore(double minScore) {
        this.minScore = minScore;
    }

    public int getMaxContentChars() {
        return maxContentChars;
    }

    public void setMaxContentChars(int maxContentChars) {
        this.maxContentChars = maxContentChars;
    }
}
