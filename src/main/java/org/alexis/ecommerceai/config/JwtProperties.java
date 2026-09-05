package org.alexis.ecommerceai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Propiedades JWT en una sola fuente ({@code app.jwt}).
 * Reemplaza el secreto duplicado entre {@code SecurityConfig} y el login:
 * el secret y la expiración se leen de aquí en todos los componentes.
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String secret,
        Duration expiration
) {
}
