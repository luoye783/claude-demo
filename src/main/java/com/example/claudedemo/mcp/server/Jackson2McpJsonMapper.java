package com.example.claudedemo.mcp.server;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.TypeRef;

/**
 * 基于 Jackson 2 的 {@link McpJsonMapper} 实现.
 *
 * <p><b>为什么不直接用 {@code mcp-json-jackson3}</b>:
 * MCP 1.1.3 内置的 {@code Jackson3McpJsonMapper} 依赖 Jackson 3({@code tools.jackson.*}),
 * 运行时尝试读取 Jackson 2 的 {@code com.fasterxml.jackson.annotation.JsonFormat$Shape.POJO}
 * 字段,触发 {@code NoSuchFieldError}.这是 MCP 1.1.x 与 Jackson 2 共存 classpath 时的已知问题.
 *
 * <p>本类复用 Spring Boot 3.3.5 自带的 Jackson 2.17.2,完全规避冲突.后续如 MCP 升级到 2.x
 * 并解决 Jackson 兼容性,可换回 {@code mcp-json-jackson3}.
 *
 * <p>该类同时实现 {@link io.modelcontextprotocol.json.McpJsonMapperSupplier},
 * 通过 SPI 注册,被 MCP 内部 {@code McpServiceLoader} 加载;
 * 实际用法见 {@link McpServerFactory#start()}.
 *
 * @author claude-code
 * @since 0.0.1
 */
public class Jackson2McpJsonMapper implements McpJsonMapper, io.modelcontextprotocol.json.McpJsonMapperSupplier {

    private final ObjectMapper delegate;

    public Jackson2McpJsonMapper() {
        this(new ObjectMapper());
    }

    public Jackson2McpJsonMapper(ObjectMapper delegate) {
        this.delegate = delegate;
    }

    @Override
    public <T> T readValue(String src, Class<T> valueType) {
        try {
            return delegate.readValue(src, valueType);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read JSON value as " + valueType.getName(), e);
        }
    }

    @Override
    public <T> T readValue(byte[] src, Class<T> valueType) {
        try {
            return delegate.readValue(src, valueType);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read JSON bytes as " + valueType.getName(), e);
        }
    }

    @Override
    public <T> T readValue(String src, TypeRef<T> typeRef) {
        try {
            // MCP TypeRef 包装 Jackson 2 TypeReference
            return delegate.readValue(src, toJacksonTypeRef(typeRef));
        } catch (Exception e) {
            throw new RuntimeException("Failed to read JSON value as " + typeRef, e);
        }
    }

    @Override
    public <T> T readValue(byte[] src, TypeRef<T> typeRef) {
        try {
            return delegate.readValue(src, toJacksonTypeRef(typeRef));
        } catch (Exception e) {
            throw new RuntimeException("Failed to read JSON bytes as " + typeRef, e);
        }
    }

    @Override
    public <T> T convertValue(Object fromValue, Class<T> valueType) {
        return delegate.convertValue(fromValue, valueType);
    }

    @Override
    public <T> T convertValue(Object fromValue, TypeRef<T> typeRef) {
        return delegate.convertValue(fromValue, toJacksonTypeRef(typeRef));
    }

    @Override
    public String writeValueAsString(Object value) {
        try {
            return delegate.writeValueAsString(value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to write value as JSON string", e);
        }
    }

    @Override
    public byte[] writeValueAsBytes(Object value) {
        try {
            return delegate.writeValueAsBytes(value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to write value as JSON bytes", e);
        }
    }

    /**
     * SPI 入口:返回自身,MCP 内部如走 {@code ServiceLoader} 也能拿到.
     */
    @Override
    public McpJsonMapper get() {
        return this;
    }

    /**
     * 把 MCP 的 {@link TypeRef} 适配成 Jackson 2 的 {@link TypeReference}.
     *
     * <p>两个类结构相同(都是持有 {@code java.lang.reflect.Type}),通过反射拿 type 字段.
     */
    private static <T> TypeReference<T> toJacksonTypeRef(TypeRef<T> mcpTypeRef) {
        try {
            java.lang.reflect.Field typeField = TypeRef.class.getDeclaredField("type");
            typeField.setAccessible(true);
            java.lang.reflect.Type t = (java.lang.reflect.Type) typeField.get(mcpTypeRef);
            return new TypeReference<T>() {
                @Override
                public java.lang.reflect.Type getType() {
                    return t;
                }
            };
        } catch (Exception e) {
            throw new RuntimeException("Failed to adapt MCP TypeRef to Jackson TypeReference", e);
        }
    }
}
