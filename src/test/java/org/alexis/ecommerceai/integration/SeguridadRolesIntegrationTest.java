package org.alexis.ecommerceai.integration;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Criterio de aceptación del Bloque 4: ningún endpoint de administración es
 * accesible con un JWT de rol {@code ROLE_CLIENTE}, <b>ni siquiera falsificando
 * cabeceras o el payload del token</b>.
 *
 * <p>Se cubren los tres vectores habituales de escalada de privilegios:</p>
 * <ol>
 *   <li>cabecera {@code X-Role: ADMIN} (no existe en ningún filtro);</li>
 *   <li>parámetro de query {@code ?rol=ADMIN};</li>
 *   <li>JWT con {@code roles:[ROLE_ADMIN]} firmado con una clave distinta
 *       (firma inválida ⇒ token rechazado).</li>
 * </ol>
 *
 * <p>Además se verifica que el hash BCrypt del usuario no aparece en ninguna
 * respuesta JSON de usuario.</p>
 */
@Transactional
class SeguridadRolesIntegrationTest extends AbstractIntegrationTest {

    private static final String SECRETO_TEST = "clave-secreta-test-de-256-bits-para-tests";
    private static final String SECRETO_ATACANTE = "clave-del-atacante-que-no-es-la-del-servidor";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private void registrar(String username) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"secreta123\"}".formatted(username)))
                .andExpect(status().isCreated());
    }

    private String login(String username, String password) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    private static String jwtFirmado(String secreto, String subject, List<String> roles) {
        try {
            SecretKeySpec key = new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader(JWSAlgorithm.HS256),
                    new JWTClaimsSet.Builder().subject(subject).claim("roles", roles).build());
            jwt.sign(new MACSigner(key));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo firmar el JWT de prueba", e);
        }
    }

    private long crearPedido(String token, long productoId) throws Exception {
        String response = mockMvc.perform(post("/api/v1/pedidos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productoId\":%d,\"cantidad\":1}]}".formatted(productoId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    // ---------- vectores de escalada de privilegios ----------

    @Test
    void cliente_conHeaderXRolesAdmin_noAccedeAEndpointsDeAdministracion() throws Exception {
        registrar("juan");
        String tokenAdmin = login("admin", "admin123");
        String tokenCliente = login("juan", "secreta123");
        long categoriaId = objectMapper.readTree(mockMvc.perform(post("/api/v1/categorias")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"CAT-SEC-1\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        long productoId = objectMapper.readTree(mockMvc.perform(post("/api/v1/productos")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"SKU-SEC-1","nombre":"Producto SEC","precio":10.00,"stock":5,
                                 "descripcionTecnica":"t","descripcionColoquial":"c","categoriaId":%d}
                                """.formatted(categoriaId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        long pedidoId = crearPedido(tokenCliente, productoId);

        // 1) POST de producto con cabecera falsificada
        mockMvc.perform(post("/api/v1/productos")
                        .header("Authorization", "Bearer " + tokenCliente)
                        .header("X-Role", "ADMIN")
                        .header("X-Roles", "ROLE_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"SKU-SEC-FAKE","nombre":"Intruso","precio":1.00,"stock":1,
                                 "descripcionTecnica":"t","descripcionColoquial":"c"}
                                """))
                .andExpect(status().isForbidden());

        // 2) Listado de usuarios con cabecera falsificada (y sin token)
        mockMvc.perform(get("/api/v1/usuarios")
                        .header("Authorization", "Bearer " + tokenCliente)
                        .header("X-Role", "ADMIN"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/usuarios")
                        .header("X-Role", "ADMIN"))
                .andExpect(status().isForbidden());

        // 3) Transición de estado de pedido con cabecera falsificada
        mockMvc.perform(patch("/api/v1/pedidos/" + pedidoId + "/estado")
                        .header("Authorization", "Bearer " + tokenCliente)
                        .header("X-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"CONFIRMADO\"}"))
                .andExpect(status().isForbidden());

        // 4) Cabecera falsificada + parámetro de query con el rol
        mockMvc.perform(get("/api/v1/usuarios?rol=ADMIN&role=ADMIN")
                        .header("Authorization", "Bearer " + tokenCliente)
                        .header("X-Role", "ADMIN"))
                .andExpect(status().isForbidden());

        // El pedido sigue en PENDIENTE: ninguna de las peticiones tuvo efecto
        mockMvc.perform(get("/api/v1/pedidos/" + pedidoId)
                        .header("Authorization", "Bearer " + tokenCliente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"));
    }

    @Test
    void jwtConRolAdminPeroFirmadoConOtraClave_esRechazado() throws Exception {
        String tokenFalsificado = jwtFirmado(SECRETO_ATACANTE, "admin", List.of("ROLE_ADMIN"));

        mockMvc.perform(get("/api/v1/usuarios")
                        .header("Authorization", "Bearer " + tokenFalsificado))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/categorias")
                        .header("Authorization", "Bearer " + tokenFalsificado)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"CAT-HACK\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void jwtValidoDeAdmin_conRolTomadoDelToken_accedeYTransicionaEstado() throws Exception {
        registrar("juan");
        String tokenAdmin = login("admin", "admin123");
        String tokenCliente = login("juan", "secreta123");
        long categoriaId = objectMapper.readTree(mockMvc.perform(post("/api/v1/categorias")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"CAT-SEC-2\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        long productoId = objectMapper.readTree(mockMvc.perform(post("/api/v1/productos")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"SKU-SEC-2","nombre":"Producto SEC 2","precio":10.00,"stock":5,
                                 "descripcionTecnica":"t","descripcionColoquial":"c","categoriaId":%d}
                                """.formatted(categoriaId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        long pedidoId = crearPedido(tokenCliente, productoId);

        mockMvc.perform(patch("/api/v1/pedidos/" + pedidoId + "/estado")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"CONFIRMADO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CONFIRMADO"));

        // Transición imposible desde CONFIRMADO a ENTREGADO → 409
        mockMvc.perform(patch("/api/v1/pedidos/" + pedidoId + "/estado")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"ENTREGADO\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        mockMvc.perform(get("/api/v1/usuarios")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.username == 'admin')].username").exists());
    }

    // ---------- el password nunca se serializa ----------

    @Test
    void usuarioAutenticado_meNuncaDevuelvePasswordNiHash() throws Exception {
        registrar("juan");
        String tokenCliente = login("juan", "secreta123");

        String cuerpo = mockMvc.perform(get("/api/v1/usuarios/me")
                        .header("Authorization", "Bearer " + tokenCliente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("juan"))
                .andExpect(jsonPath("$.rol").value("CLIENTE"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(cuerpo)
                .doesNotContain("password")
                .doesNotContain("$2a$")
                .doesNotContain("$2b$");
    }

    @Test
    void listadoDeUsuariosComoAdmin_nuncaDevuelvePassword() throws Exception {
        registrar("juan");
        String tokenAdmin = login("admin", "admin123");

        String cuerpo = mockMvc.perform(get("/api/v1/usuarios")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("password"))))
                .andExpect(content().string(not(containsString("$2a$"))))
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(cuerpo)
                .contains("\"username\":\"admin\"")
                .doesNotContain("password");
    }

    @Test
    void registroYLogin_tampocoDevuelvenPassword() throws Exception {
        String registro = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nuevo\",\"password\":\"secreta123\",\"email\":\"nuevo@example.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("nuevo@example.com"))
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(registro).doesNotContain("password").doesNotContain("$2");

        String login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nuevo\",\"password\":\"secreta123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(login).doesNotContain("password").doesNotContain("$2");
    }

    @Test
    void registro_conEmailInvalido_devuelve400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"malemail\",\"password\":\"secreta123\",\"email\":\"no-es-un-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").value("El email no tiene un formato válido"));
    }

    @Test
    void registro_conPasswordCorta_devuelve400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"corta\",\"password\":\"1234\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password")
                        .value("La contraseña debe tener al menos 8 caracteres"));
    }

    @Test
    void carrito_conCantidadCeroOPositiva_lasReglasDeValidacionAplican() throws Exception {
        registrar("juan");
        String tokenCliente = login("juan", "secreta123");

        mockMvc.perform(post("/api/v1/carrito/items")
                        .header("Authorization", "Bearer " + tokenCliente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productoId\":1,\"cantidad\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.cantidad").value("La cantidad debe ser mayor a cero"));

        mockMvc.perform(post("/api/v1/carrito/items")
                        .header("Authorization", "Bearer " + tokenCliente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productoId\":1,\"cantidad\":-3}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void usuariosSinToken_403() throws Exception {
        mockMvc.perform(get("/api/v1/usuarios")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/usuarios/me")).andExpect(status().isForbidden());
    }
}
