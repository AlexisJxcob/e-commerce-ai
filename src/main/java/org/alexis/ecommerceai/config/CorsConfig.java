package org.alexis.ecommerceai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Configuración centralizada de CORS para la aplicación.
 * Spring Security la consume a través de {@code http.cors(Customizer.withDefaults())}.
 * Permite orígenes explícitos configurados vía {@code app.cors.allowed-origins} (env {@code CORS_ORIGINS}).
 */
@Configuration
public class CorsConfig {

    private final List<String> allowedOrigins;
    private final List<String> allowedMethods;
    private final List<String> allowedHeaders;
    private final long maxAge;

    public CorsConfig(
            @Value("${app.cors.allowed-origins:http://localhost:3001,http://localhost:3000}") String allowedOrigins,
            @Value("${app.cors.allowed-methods:GET,POST,PUT,PATCH,DELETE,OPTIONS}") String allowedMethods,
            @Value("${app.cors.allowed-headers:Authorization,Content-Type,Accept}") String allowedHeaders,
            @Value("${app.cors.max-age:3600}") long maxAge) {
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
        this.allowedMethods = Arrays.stream(allowedMethods.split(","))
                .map(String::trim)
                .filter(m -> !m.isEmpty())
                .toList();
        this.allowedHeaders = Arrays.stream(allowedHeaders.split(","))
                .map(String::trim)
                .filter(h -> !h.isEmpty())
                .toList();
        this.maxAge = maxAge;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(allowedMethods);
        configuration.setAllowedHeaders(allowedHeaders.contains("*") ? List.of("*") : allowedHeaders);
        configuration.setMaxAge(maxAge);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}