package org.alexis.ecommerceai.service;

import org.alexis.ecommerceai.config.JwtProperties;
import org.alexis.ecommerceai.dto.LoginResponse;
import org.alexis.ecommerceai.dto.RegisterRequestDTO;
import org.alexis.ecommerceai.dto.UsuarioResponseDTO;
import org.alexis.ecommerceai.exception.CredencialesInvalidasException;
import org.alexis.ecommerceai.exception.UsuarioDuplicadoException;
import org.alexis.ecommerceai.model.Rol;
import org.alexis.ecommerceai.model.Usuario;
import org.alexis.ecommerceai.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;

    public AuthService(UsuarioRepository usuarioRepository,
                       PasswordEncoder passwordEncoder,
                       JwtEncoder jwtEncoder,
                       JwtProperties jwtProperties) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.jwtProperties = jwtProperties;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(String username, String password) {
        var usuario = usuarioRepository.findByUsername(username).orElse(null);
        if (usuario == null || !passwordEncoder.matches(password, usuario.getPassword())) {
            throw new CredencialesInvalidasException();
        }
        long ttlSegundos = jwtProperties.expiration().toSeconds();
        return new LoginResponse(emitirToken(usuario), usuario.getUsername(), ttlSegundos);
    }

    @Transactional
    public UsuarioResponseDTO register(RegisterRequestDTO request) {
        if (usuarioRepository.existsByUsername(request.username())) {
            throw new UsuarioDuplicadoException("Ya existe un usuario con el nombre: " + request.username());
        }
        var usuario = new Usuario();
        usuario.setUsername(request.username());
        usuario.setPassword(passwordEncoder.encode(request.password()));
        usuario.setRol(Rol.CLIENTE);
        usuario = usuarioRepository.save(usuario);
        return new UsuarioResponseDTO(usuario.getId(), usuario.getUsername(), usuario.getRol().name());
    }

    private String emitirToken(Usuario usuario) {
        Instant ahora = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(usuario.getUsername())
                .issuedAt(ahora)
                .expiresAt(ahora.plus(jwtProperties.expiration()))
                // Formato esperado por JwtAuthenticationFilter.getClaimAsStringList("roles"):
                // cada entrada se convierte en una SimpleGrantedAuthority, por lo que
                // para satisfacer hasRole("ADMIN") debe contener el literal "ROLE_ADMIN".
                .claim("roles", List.of("ROLE_" + usuario.getRol().name()))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
