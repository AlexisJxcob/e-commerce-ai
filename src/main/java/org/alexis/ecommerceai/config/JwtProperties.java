package org.alexis.ecommerceai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Propiedades JWT en una sola fuente ({@code app.jwt}).
 *
 * <p>El secreto se inyecta desde la propiedad {@code app.jwt.secret} (o variable {@code JWT_SECRET}).
 * Si falta o tiene menos de 256 bits (32 bytes UTF-8), la aplicación falla al arrancar (fail-fast)
 * en lugar de operar con un secreto inseguro.</p>
 *
 * <p>{@code expiration} define la vigencia del token (ej. 86400000 ms = 24h).</p>
 * <p>{@code issuer} es opcional: si se define, los tokens deben incluir un claim {@code iss} coincidente.</p>
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String secret,
        Duration expiration,
        String issuer
) {

    /** Mínimo exigido por HS256: 256 bits = 32 bytes. */
    public static final int MIN_SECRET_BYTES = 32;

    public JwtProperties(String secret, Duration expiration) {
        this(secret, expiration, null);
    }

    public JwtProperties {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "La propiedad app.jwt.secret (variable JWT_SECRET) es obligatoria para firmar/verificar tokens JWT. "
                            + "Defínela antes de arrancar la aplicación.");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "El secreto JWT debe tener al menos 256 bits (32 caracteres UTF-8). "
                            + "Genéralo con: openssl rand -hex 32");
        }
    }
}
