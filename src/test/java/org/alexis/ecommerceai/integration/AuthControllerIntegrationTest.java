package org.alexis.ecommerceai.integration;

import org.alexis.ecommerceai.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cobertura de integración del slice de autenticación (spec user-auth):
 * seed del admin, login contra BD, 401 indistinguible, registro público
 * CLIENTE y enforcement de roles en mutaciones del catálogo.
 */
@Transactional
class AuthControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtDecoder jwtDecoder;

    private String login(String username, String password) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    @Test
    void arranque_creaAdminConPasswordCifrada() {
        var admin = usuarioRepository.findByUsername("admin");
        assertThat(admin).isPresent();
        assertThat(admin.get().getRol().name()).isEqualTo("ADMIN");
        assertThat(admin.get().getPassword()).isNotEqualTo("admin123");
        assertThat(passwordEncoder.matches("admin123", admin.get().getPassword())).isTrue();
    }

    @Test
    void login_adminValido_devuelveTokenConRolAdmin() throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andReturn().getResponse().getContentAsString();
        JsonNode body = objectMapper.readTree(response);
        var jwt = jwtDecoder.decode(body.get("token").asText());
        assertThat(jwt.getSubject()).isEqualTo("admin");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("ROLE_ADMIN");
        assertThat(body.get("expiresIn").asLong()).isEqualTo(86400L);
    }

    @Test
    void login_passwordMalaYUsuarioDesconocido_devuelvenMismo401() throws Exception {
        String mala = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"incorrecta\"}"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        String desconocido = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nadie\",\"password\":\"incorrecta\"}"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(mala).get("message").asText())
                .isEqualTo(objectMapper.readTree(desconocido).get("message").asText());
    }

    @Test
    void register_creaClienteQuePuedeOperarComoCliente() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"juan\",\"password\":\"secreta123\",\"rol\":\"ADMIN\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("juan"))
                .andExpect(jsonPath("$.rol").value("CLIENTE"))
                .andExpect(jsonPath("$.password").doesNotExist());

        String tokenCliente = login("juan", "secreta123");
        var jwt = jwtDecoder.decode(tokenCliente);
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("ROLE_CLIENTE");

        // El CLIENTE no puede mutar el catálogo
        mockMvc.perform(post("/api/v1/productos")
                        .header("Authorization", "Bearer " + tokenCliente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"SKU-CLI","nombre":"X","precio":1.0,"stock":1,
                                 "descripcionTecnica":"t","descripcionColoquial":"c","categoriaId":1}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void register_usernameDuplicado_devuelve409() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"secreta123\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void tokenAdminSembrado_sigueOperandoMutaciones() throws Exception {
        String tokenAdmin = login("admin", "admin123");

        mockMvc.perform(get("/api/v1/categorias")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk());
    }
}
