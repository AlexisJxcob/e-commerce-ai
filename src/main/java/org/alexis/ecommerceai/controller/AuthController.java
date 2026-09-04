package org.alexis.ecommerceai.controller;

import jakarta.validation.Valid;
import org.alexis.ecommerceai.dto.LoginRequest;
import org.alexis.ecommerceai.dto.LoginResponse;
import org.alexis.ecommerceai.exception.CredencialesInvalidasException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/auth")
public class AuthController {

    /**
     * Usuario en memoria mientras no exista la entidad Usuario en el dominio.
     * En el futuro reemplazar por la autenticación real (UserDetailsService).
     */
    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "admin123";
    private static final long TOKEN_TTL_SECONDS = 3600;

    private final String jwtSecret;

    public AuthController(@Value("${app.jwt.secret:clave-secreta-de-256-bits-para-jwt}") String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        if (!ADMIN_USERNAME.equals(request.username()) || !ADMIN_PASSWORD.equals(request.password())) {
            throw new CredencialesInvalidasException();
        }
        String token = emitirToken(request.username());
        var response = new LoginResponse(token, request.username(), TOKEN_TTL_SECONDS);
        return ResponseEntity.ok(response);
    }

    private String emitirToken(String username) {
        var key = new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        JwtEncoder encoder = NimbusJwtEncoder.withSecretKey(key).build();

        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(username)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(TOKEN_TTL_SECONDS))
                // Formato esperado por JwtAuthenticationFilter.getClaimAsStringList("roles"):
                // cada entrada se convierte en una SimpleGrantedAuthority, por lo que
                // para satisfacer hasRole("ADMIN") debe contener el literal "ROLE_ADMIN".
                .claim("roles", List.of("ROLE_ADMIN"))
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
