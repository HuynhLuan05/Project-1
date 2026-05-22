package com.yas.search.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchConfiguration;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;
import org.springframework.data.elasticsearch.support.HttpHeaders;

@Configuration
@EnableElasticsearchRepositories(basePackages = "com.yas.search.repository")
@ComponentScan(basePackages = "com.yas.search.service")
@RequiredArgsConstructor
public class ImperativeClientConfig extends ElasticsearchConfiguration {

    private static final String ELASTICSEARCH_V8_MEDIA_TYPE =
            "application/vnd.elasticsearch+json;compatible-with=8";

    private final ElasticsearchDataConfig elasticsearchConfig;

    @Override
    public ClientConfiguration clientConfiguration() {
        HttpHeaders defaultHeaders = new HttpHeaders();
        defaultHeaders.add("Accept", ELASTICSEARCH_V8_MEDIA_TYPE);
        defaultHeaders.add("Content-Type", ELASTICSEARCH_V8_MEDIA_TYPE);

        return ClientConfiguration.builder()
                .connectedTo(elasticsearchConfig.getUrl())
                .withBasicAuth(elasticsearchConfig.getUsername(), elasticsearchConfig.getPassword())
                .withDefaultHeaders(defaultHeaders)
                .build();
    }
}
