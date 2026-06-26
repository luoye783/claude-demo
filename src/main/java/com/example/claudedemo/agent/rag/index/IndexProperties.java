package com.example.claudedemo.agent.rag.index;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 索引生命周期配置(V2 第十一阶段 RAG V6).
 *
 * <p>对应 application.yml 中的 {@code rag.index} 配置块。
 *
 * <p><b>注意</b>:{@code auto-index-on-startup} 配置项已定义但本阶段不实现自动启动,
 * 仅手动调用 {@link KnowledgeIndexService}。
 *
 * @since 0.0.1
 */
@ConfigurationProperties(prefix = "rag.index")
public class IndexProperties {

    /** 启动时是否自动执行全量索引(V6 暂不实现自动). */
    private boolean autoIndexOnStartup = false;

    /** 增量索引后是否自动清理已删除文档对应的向量. */
    private boolean cleanupRemovedDocuments = true;

    public boolean isAutoIndexOnStartup() {
        return autoIndexOnStartup;
    }

    public void setAutoIndexOnStartup(boolean autoIndexOnStartup) {
        this.autoIndexOnStartup = autoIndexOnStartup;
    }

    public boolean isCleanupRemovedDocuments() {
        return cleanupRemovedDocuments;
    }

    public void setCleanupRemovedDocuments(boolean cleanupRemovedDocuments) {
        this.cleanupRemovedDocuments = cleanupRemovedDocuments;
    }
}
