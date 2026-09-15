package org.alexis.ecommerceai.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(WebpayProperties.class)
public class WebpayConfig {

    @Bean(name = "webpayRestClient")
    public RestClient webpayRestClient(RestClient.Builder builder, WebpayProperties properties) {
        var requestFactory = new JdkClientHttpRequestFactory();
        requestFactory.setReadTimeout(Duration.ofSeconds(30));

        return builder.clone()
                .requestFactory(requestFactory)
                .baseUrl(properties.baseUrl())
                .defaultHeader("Tbk-Api-Key-Id", properties.commerceCode())
                .defaultHeader("Tbk-Api-Key-Secret", properties.apiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
