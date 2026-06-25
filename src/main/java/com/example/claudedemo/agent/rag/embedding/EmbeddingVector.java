package com.example.claudedemo.agent.rag.embedding;

import java.util.Arrays;

/**
 * 嵌入向量值对象(V2 第八阶段 RAG V3).
 *
 * <p>封装 {@code float[]} 向量与显式维度信息。
 * 不可变 —— {@link #values()} 返回防御性拷贝。
 *
 * <p><b>维度校验</b>:构造时 {@code values.length} 必须等于 {@code dimension},
 * 否则抛 {@link IllegalArgumentException}。维度不一致是严重错误,不能静默修正。
 *
 * @since 0.0.1
 */
public record EmbeddingVector(
        float[] values,
        int dimension
) {

    public EmbeddingVector {
        if (values == null) {
            throw new IllegalArgumentException("values must not be null");
        }
        if (dimension <= 0) {
            throw new IllegalArgumentException("dimension must be > 0, actual: " + dimension);
        }
        if (values.length != dimension) {
            throw new IllegalArgumentException(
                    "values.length (" + values.length + ") != dimension (" + dimension + ")");
        }
        // 防御性拷贝:防止外部修改内部数组
        values = values.clone();
    }

    /**
     * 返回防御性拷贝,防止外部修改内部数组.
     */
    @Override
    public float[] values() {
        return values.clone();
    }

    /**
     * 基于 values 内容的 equality(数组内容比较而非引用).
     */
    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EmbeddingVector that)) return false;
        return dimension == that.dimension && Arrays.equals(values, that.values);
    }

    @Override
    public final int hashCode() {
        int result = Arrays.hashCode(values);
        result = 31 * result + dimension;
        return result;
    }

    @Override
    public String toString() {
        return "EmbeddingVector{dim=" + dimension + ", values=[" + values[0] + ", " + values[1] + ", ...]}";
    }
}
