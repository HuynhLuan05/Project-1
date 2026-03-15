package com.yas.recommendation.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;

@Getter
@Setter
@ConfigurationProperties(prefix = "spring.ai.vectorstore.pgvector")
public class VectorStoreProperties {
    private int dimensions;
    private PgVectorStore.PgDistanceType distanceType;
    private PgVectorStore.PgIndexType indexType;
    private boolean initializeSchema;
}
