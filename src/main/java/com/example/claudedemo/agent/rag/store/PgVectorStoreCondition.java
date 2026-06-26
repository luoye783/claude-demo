package com.example.claudedemo.agent.rag.store;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * 条件判断:当前 provider=pgvector 时才装配 {@link PgVectorStoreConfig}.
 *
 * @since 0.0.1
 */
public class PgVectorStoreCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String provider = context.getEnvironment().getProperty("vector-store.provider");
        return "pgvector".equalsIgnoreCase(provider);
    }
}
