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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String SECRETO_TEST = "clave-secreta-test-de-32-bytes-o-mas!!";
    private static final Duration EXPIRACION = Duration.ofMillis(86_400_000);

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private JwtEncoder jwtEncoder;
    private JwtDecoder jwtDecoder;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        var key = new SecretKeySpec(SECRETO_TEST.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        jwtEncoder = NimbusJwtEncoder.withSecretKey(key).build();
        jwtDecoder = NimbusJwtDecoder.withSecretKey(key).build();
        authService = new AuthService(usuarioRepository, passwordEncoder, jwtEncoder,
                new JwtProperties(SECRETO_TEST, EXPIRACION, null));
    }

    private static Usuario usuario(String username, Rol rol) {
        Usuario usuario = new Usuario();
        usuario.setId(1L);
        usuario.setUsername(username);
        usuario.setPassword("$2a$10$hashalmacenado");
        usuario.setRol(rol);
        return usuario;
    }

    // ---------- login ----------

    @Test
    void login_adminValido_devuelveTokenConRolAdminYExpiracionConfigurada() {
        Usuario admin = usuario("admin", Rol.ADMIN);
        when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("admin123", admin.getPassword())).thenReturn(true);

        Instant antes = Instant.now();
        LoginResponse response = authService.login("admin", "admin123");

        assertThat(response.username()).isEqualTo("admin");
        assertThat(response.expiresIn()).isEqualTo(EXPIRACION.toSeconds());
        Jwt jwt = jwtDecoder.decode(response.token());
        assertThat(jwt.getSubject()).isEqualTo("admin");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("ROLE_ADMIN");
        assertThat(jwt.getExpiresAt()).isAfter(antes.plusSeconds(EXPIRACION.toSeconds() - 60));
    }

    @Test
    void login_conPasswordIncorrecta_lanza401() {
        Usuario admin = usuario("admin", Rol.ADMIN);
        when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("mala", admin.getPassword())).thenReturn(false);

        assertThatThrownBy(() -> authService.login("admin", "mala"))
                .isInstanceOf(CredencialesInvalidasException.class)
                .hasMessage("Credenciales inválidas");
    }

    @Test
    void login_conUsuarioDesconocido_lanzaMismo401SinEnumerar() {
        when(usuarioRepository.findByUsername("fantasma")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login("fantasma", "cualquiera"))
                .isInstanceOf(CredencialesInvalidasException.class)
                .hasMessage("Credenciales inválidas");
    }

    @Test
    void login_clienteValido_devuelveSoloRolCliente() {
        Usuario cliente = usuario("juan", Rol.CLIENTE);
        when(usuarioRepository.findByUsername("juan")).thenReturn(Optional.of(cliente));
        when(passwordEncoder.matches("secreta123", cliente.getPassword())).thenReturn(true);

        LoginResponse response = authService.login("juan", "secreta123");

        Jwt jwt = jwtDecoder.decode(response.token());
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("ROLE_CLIENTE");
    }

    // ---------- register ----------

    @Test
    void register_usuarioNuevo_creaClienteConPasswordCifrada() {
        when(usuarioRepository.existsByUsername("juan")).thenReturn(false);
        when(passwordEncoder.encode("secreta123")).thenReturn("$2a$10$hashnuevo");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> {
            Usuario guardado = invocation.getArgument(0);
            guardado.setId(2L);
            return guardado;
        });

        UsuarioResponseDTO response = authService.register(new RegisterRequestDTO("juan", "secreta123", null, null));

        assertThat(response.id()).isEqualTo(2L);
        assertThat(response.username()).isEqualTo("juan");
        assertThat(response.rol()).isEqualTo("CLIENTE");
        verify(usuarioRepository).save(any(Usuario.class));
    }

    @Test
    void register_conHintDeAdmin_igualCreaCliente() {
        when(usuarioRepository.existsByUsername("vivo")).thenReturn(false);
        when(passwordEncoder.encode("secreta123")).thenReturn("$2a$10$hashnuevo");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UsuarioResponseDTO response = authService.register(new RegisterRequestDTO("vivo", "secreta123", "ADMIN", null));

        assertThat(response.rol()).isEqualTo("CLIENTE");
    }

    @Test
    void register_conUsernameDuplicado_lanza409() {
        when(usuarioRepository.existsByUsername("juan")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequestDTO("juan", "secreta123", null, null)))
                .isInstanceOf(UsuarioDuplicadoException.class)
                .hasMessageContaining("juan");
        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    void register_noAlmacenaPasswordEnTextoPlano() {
        when(usuarioRepository.existsByUsername("juan")).thenReturn(false);
        when(passwordEncoder.encode("secreta123")).thenAnswer(invocation -> "cifrado:" + invocation.getArgument(0));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        authService.register(new RegisterRequestDTO("juan", "secreta123", null, null));

        var captor = org.mockito.ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        assertThat(captor.getValue().getPassword()).isNotEqualTo("secreta123");
        assertThat(captor.getValue().getRol()).isEqualTo(Rol.CLIENTE);
    }
}
