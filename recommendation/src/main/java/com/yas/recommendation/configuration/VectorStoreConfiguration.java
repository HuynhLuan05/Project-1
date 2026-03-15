package com.yas.recommendation.configuration;

import lombok.AllArgsConstructor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * VectorStore configuration that provides a manual VectorStore bean.
 */
@Configuration
@EnableConfigurationProperties(VectorStoreProperties.class)
@AllArgsConstructor
public class VectorStoreConfiguration {

    private final VectorStoreProperties vectorStoreProperties;

    @Bean
    @ConditionalOnMissingBean(VectorStore.class)
    public VectorStore vectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
            .dimensions(vectorStoreProperties.getDimensions())
            .distanceType(vectorStoreProperties.getDistanceType())
            .indexType(vectorStoreProperties.getIndexType())
            .initializeSchema(vectorStoreProperties.isInitializeSchema())
            .build();
    }
}
