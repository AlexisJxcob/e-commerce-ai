
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

    @Bean(name = "openRouterRestClient")
    public RestClient openRouterRestClient(RestClient.Builder builder, OpenRouterProperties properties) {
        String key = properties.getKey() == null ? "" : properties.getKey();
        return builder.clone()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + key)
                .defaultHeader("HTTP-Referer", properties.getHttpReferer())
                .defaultHeader("X-Title", properties.getAppTitle())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
