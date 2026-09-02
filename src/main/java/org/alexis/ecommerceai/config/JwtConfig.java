package org.alexis.ecommerceai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Configuración centralizada para JWT.
 * Permite inyectar propiedades relacionadas con JWT en otros componentes.
 */
@Configuration
public class JwtConfig {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration:86400000}")
    private long expirationMillis;

    @Bean
    public JwtProperties jwtProperties() {
        return new JwtProperties(secret, Duration.ofMillis(expirationMillis));
    }

    public record JwtProperties(String secret, Duration expiration) {
        public String getSecret() {
            return secret;
        }

        public Duration getExpiration() {
            return expiration;
        }
    }
}