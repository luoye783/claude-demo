package com.example.claudedemo.agent.rag.index;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * SHA-256 哈希工具(V2 第十一阶段 RAG V6).
 *
 * <p>用于计算文档和 chunk 的内容指纹,判断内容是否变化,
 * 决定是否需要重新 embedding 和 upsert。
 *
 * <p><b>规则</b>:
 * <ul>
 *   <li>{@link #documentHash(String)}: 基于文档全文 content</li>
 *   <li>{@link #chunkHash(String, String, int)}: 基于 chunk content + source + chunkIndex</li>
 *   <li>相同输入 → 相同 hex 输出(确定性)</li>
 *   <li>空/null 输入 → 空字符串 hash</li>
 * </ul>
 *
 * @since 0.0.1
 */
public final class HashUtils {

    private HashUtils() {}

    /**
     * 计算文档级别的 SHA-256 哈希,基于完整 content.
     *
     * @param content 文档全文
     * @return 64 字符 hex 字符串
     */
    public static String documentHash(String content) {
        return sha256(content == null ? "" : content);
    }

    /**
     * 计算 chunk 级别的 SHA-256 哈希.
     *
     * <p>输入组合 = content + "|" + source + "|" + chunkIndex,
     * 确保不同文档中相同文本但不同位置/source 的 chunk 有不同 hash。
     *
     * @param content    chunk 文本
     * @param source     来源路径
     * @param chunkIndex 在文档中的顺序
     * @return 64 字符 hex 字符串
     */
    public static String chunkHash(String content, String source, int chunkIndex) {
        String input = (content == null ? "" : content)
                + "|" + (source == null ? "" : source)
                + "|" + chunkIndex;
        return sha256(input);
    }

    /**
     * SHA-256 → hex 字符串.
     */
    static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
