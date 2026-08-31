package org.alexis.ecommerceai.config;

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

        return builder.clone()
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + (properties.getKey() == null ? "" : properties.getKey()))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Bean
    public EmbeddingModel huggingFaceEmbeddingModel(RestClient huggingFaceRestClient, HuggingFaceProperties properties) {
        return new HuggingFaceEmbeddingModel(huggingFaceRestClient, properties);
    }
}
