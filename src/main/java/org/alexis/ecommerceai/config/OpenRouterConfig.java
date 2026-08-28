package org.alexis.ecommerceai.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(OpenRouterProperties.class)
public class OpenRouterConfig {

    public static final String HTTP_REFERER = "http://localhost:8080";
    public static final String APP_TITLE = "Ferreteria IA App";

    @Bean
    public RestClient openRouterRestClient(OpenRouterProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getKey())
                .defaultHeader("HTTP-Referer", HTTP_REFERER)
                .defaultHeader("X-Title", APP_TITLE)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
