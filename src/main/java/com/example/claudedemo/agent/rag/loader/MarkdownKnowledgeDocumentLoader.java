package com.example.claudedemo.agent.rag.loader;

import com.example.claudedemo.agent.rag.KnowledgeDocument;
import com.example.claudedemo.agent.rag.KnowledgeDocumentLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Markdown 知识文档加载器(V2 第七阶段 RAG V2).
 *
 * <p>从可配置路径加载 {@code .md} 文件,路径支持:
 * <ul>
 *   <li>普通相对/绝对路径: {@code knowledge-base} / {@code /data/agent/knowledge-base}</li>
 *   <li>classpath 路径: {@code classpath:knowledge-base}</li>
 *   <li>file 协议路径: {@code file:/data/agent/knowledge-base}</li>
 * </ul>
 *
 * <p><b>查找优先级</b>:
 * <ol>
 *   <li>当配置为普通相对路径(不含 {@code classpath:} / {@code file:})时:
 *       优先按文件系统相对路径读取;若不存在,fallback 到 {@code classpath:knowledge-base}</li>
 *   <li>当配置为 {@code classpath:} 或 {@code file:} 前缀时:直接按对应协议读取</li>
 * </ol>
 *
 * <p><b>解析规则</b>:
 * <ul>
 *   <li>{@code documentId} = 文件名去掉 {@code .md} 扩展名</li>
 *   <li>{@code title} = 文件第一行 {@code # title} 去掉 {@code # } 前缀;无 {@code # } 开头时取文件名</li>
 *   <li>{@code content} = 全文(含第一行 {@code # title})</li>
 *   <li>{@code source} = {@code knowledge-base/{filename}}</li>
 *   <li>{@code metadata} = {@code {"path": "实际读取路径"}}</li>
 * </ul>
 *
 * @since 0.0.1
 */
public class MarkdownKnowledgeDocumentLoader implements KnowledgeDocumentLoader {

    private static final Logger log = LoggerFactory.getLogger(MarkdownKnowledgeDocumentLoader.class);

    private static final String CLASS_PATH_PREFIX = "classpath:";
    private static final String FILE_PATH_PREFIX = "file:";
    /** 默认 classpath fallback 路径. */
    private static final String DEFAULT_CLASSPATH = "classpath:knowledge-base/";

    private final String configuredPath;
    private final ResourcePatternResolver resourceLoader;

    /**
     * @param configuredPath 配置的 knowledge-base-path
     * @param resourceLoader Spring ResourcePatternResolver,用于加载 classpath: 资源
     */
    public MarkdownKnowledgeDocumentLoader(String configuredPath, ResourcePatternResolver resourceLoader) {
        this.configuredPath = (configuredPath == null || configuredPath.isBlank()) ? "knowledge-base" : configuredPath;
        this.resourceLoader = resourceLoader;
    }

    @Override
    public List<KnowledgeDocument> load() {
        if (configuredPath.startsWith(CLASS_PATH_PREFIX)) {
            return loadFromClasspath(configuredPath.substring(CLASS_PATH_PREFIX.length()));
        }
        if (configuredPath.startsWith(FILE_PATH_PREFIX)) {
            return loadFromFilesystem(Paths.get(configuredPath.substring(FILE_PATH_PREFIX.length())));
        }
        // 普通相对/绝对路径:优先文件系统,不存在时 fallback classpath
        Path fsPath = Paths.get(configuredPath);
        if (Files.exists(fsPath) && Files.isDirectory(fsPath)) {
            return loadFromFilesystem(fsPath);
        }
        log.info("文件系统路径 {} 不存在,fallback 到 {}", configuredPath, DEFAULT_CLASSPATH);
        return loadFromClasspath(configuredPath);
    }

    private List<KnowledgeDocument> loadFromFilesystem(Path dir) {
        List<KnowledgeDocument> docs = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.md")) {
            for (Path entry : stream) {
                try {
                    KnowledgeDocument doc = parseFile(entry.getFileName().toString(),
                            Files.readString(entry, StandardCharsets.UTF_8),
                            entry.toAbsolutePath().toString());
                    docs.add(doc);
                } catch (Exception e) {
                    log.warn("跳过文件 {}: {}", entry, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warn("读取文件系统目录失败 {}: {}", dir, e.getMessage());
        }
        return List.copyOf(docs);
    }

    private List<KnowledgeDocument> loadFromClasspath(String dirPath) {
        String cpDir = dirPath.endsWith("/") ? dirPath : dirPath + "/";
        try {
            Resource[] resources = resourceLoader.getResources(cpDir + "*.md");
            if (resources == null || resources.length == 0) {
                return List.of();
            }
            List<KnowledgeDocument> docs = new ArrayList<>();
            for (Resource r : resources) {
                if (!r.exists() || !r.isReadable()) continue;
                try {
                    String filename = r.getFilename();
                    if (filename == null) continue;
                    String content = new String(r.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                    KnowledgeDocument doc = parseFile(filename, content,
                            "classpath:" + cpDir + filename);
                    docs.add(doc);
                } catch (Exception e) {
                    log.warn("跳过 classpath 文件 {}: {}", r.getDescription(), e.getMessage());
                }
            }
            return List.copyOf(docs);
        } catch (IOException e) {
            log.warn("读取 classpath 目录失败 {}: {}", cpDir, e.getMessage());
            return List.of();
        }
    }

    /**
     * 解析单个 .md 文件.
     */
    static KnowledgeDocument parseFile(String filename, String content, String source) {
        String docId = filename.endsWith(".md") ? filename.substring(0, filename.length() - 3) : filename;
        String title = extractTitle(content, docId);
        return new KnowledgeDocument(docId, title, content, source,
                Map.of("path", source));
    }

    /**
     * 从 Markdown 内容中提取第一行 # title.
     */
    static String extractTitle(String content, String fallback) {
        if (content == null || content.isBlank()) return fallback;
        String firstLine = content.lines().filter(l -> !l.isBlank()).findFirst().orElse("");
        if (firstLine.startsWith("# ")) {
            return firstLine.substring(2).trim();
        }
        return fallback;
    }
}
