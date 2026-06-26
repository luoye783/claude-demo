package com.example.claudedemo.agent.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RAG 检索配置(V2 第九阶段 RAG V4).
 *
 * <p>对应 application.yml 中的 {@code rag} 配置块,控制检索行为。
 * Embedding API 连接信息见 {@link com.example.claudedemo.agent.rag.embedding.EmbeddingProperties}。
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
