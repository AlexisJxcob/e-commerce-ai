package org.alexis.ecommerceai.config;

import org.alexis.ecommerceai.config.HuggingFaceEmbeddingModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(HuggingFaceProperties.class)
public class HuggingFaceConfig {

    @Bean(name = "huggingFaceRestClient")
    public RestClient huggingFaceRestClient(RestClient.Builder builder, HuggingFaceProperties properties) {
        var requestFactory = new JdkClientHttpRequestFactory();
        requestFactory.setReadTimeout(Duration.ofSeconds(120));

        String key = properties.getKey() == null ? "" : properties.getKey();

        return builder.clone()
                .requestFactory(requestFactory)
                .baseUrl(properties.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + key)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Bean
    public EmbeddingModel huggingFaceEmbeddingModel(RestClient huggingFaceRestClient, HuggingFaceProperties properties) {
        return new HuggingFaceEmbeddingModel(huggingFaceRestClient, properties);
    }
}