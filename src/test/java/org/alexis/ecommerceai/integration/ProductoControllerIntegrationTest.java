package org.alexis.ecommerceai.integration;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.alexis.ecommerceai.ai.OpenRouterService;
import org.alexis.ecommerceai.dto.SugerenciaFerreteriaDTO;
import org.alexis.ecommerceai.exception.OpenRouterException;
import org.alexis.ecommerceai.exception.OpenRouterRateLimitException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests de integración de {@code ProductoController} con contexto Spring completo
 * (MockMvc + @SpringBootTest), base PostgreSQL+pgvector real vía Testcontainers y
 * OpenRouter mockeado a nivel de servicio (sin llamadas HTTP reales).
 */
@Transactional
class ProductoControllerIntegrationTest extends AbstractIntegrationTest {

    /** Debe coincidir con app.jwt.secret del perfil "test". */
    private static final String SECRETO_TEST = "clave-secreta-test-de-256-bits-para-tests";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OpenRouterService openRouterService;

    // ---------- helpers ----------

    private static String jwtAdmin() {
        try {
            SecretKeySpec key = new SecretKeySpec(SECRETO_TEST.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader(JWSAlgorithm.HS256),
                    new JWTClaimsSet.Builder()
                            .subject("admin")
                            .claim("roles", List.of("ROLE_ADMIN"))
                            .build());
            jwt.sign(new MACSigner(key));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("No se pudo firmar el JWT de prueba", e);
        }
    }

    private long crearProducto(String sku, String nombre) throws Exception {
        String body = """
                {"sku":"%s","nombre":"%s","precio":10.50,"stock":5,
                 "descripcionTecnica":"Descripcion tecnica de %s","descripcionColoquial":"el coso de %s"}
                """.formatted(sku, nombre, nombre, nombre);
        String response = mockMvc.perform(post("/api/v1/productos")
                        .header("Authorization", "Bearer " + jwtAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    // ---------- lectura pública ----------

    @Test
    void listarProductos_iniciaVacio() throws Exception {
        mockMvc.perform(get("/api/v1/productos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void obtenerProducto_inexistente_devuelve404() throws Exception {
        mockMvc.perform(get("/api/v1/productos/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Producto no encontrado con id: 999999"));
    }

    @Test
    void obtenerProducto_idNoNumerico_devuelve400() throws Exception {
        mockMvc.perform(get("/api/v1/productos/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    // ---------- creación ----------

    @Test
    void crearProducto_devuelve201YEsRecuperable() throws Exception {
        long id = crearProducto("SKU-INT-1", "Cinta teflon");

        mockMvc.perform(get("/api/v1/productos/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("SKU-INT-1"))
                .andExpect(jsonPath("$.nombre").value("Cinta teflon"))
                .andExpect(jsonPath("$.precio").value(10.5))
                .andExpect(jsonPath("$.stock").value(5));
    }

    @Test
    void crearProducto_sinToken_devuelve403() throws Exception {
        mockMvc.perform(post("/api/v1/productos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"SKU-X","nombre":"X","precio":1.0,"stock":1,
                                 "descripcionTecnica":"t","descripcionColoquial":"c"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void crearProducto_tokenInvalido_devuelve403() throws Exception {
        mockMvc.perform(post("/api/v1/productos")
                        .header("Authorization", "Bearer token-invalido")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"SKU-X","nombre":"X","precio":1.0,"stock":1,
                                 "descripcionTecnica":"t","descripcionColoquial":"c"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void crearProducto_bodyInvalido_devuelve400ConErroresDeCampo() throws Exception {
        mockMvc.perform(post("/api/v1/productos")
                        .header("Authorization", "Bearer " + jwtAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"","nombre":"","precio":-1,"stock":-1,
                                 "descripcionTecnica":"","descripcionColoquial":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.sku").exists())
                .andExpect(jsonPath("$.fieldErrors.precio").exists());
    }

    // ---------- stock ----------

    @Test
    void actualizarStock_valido_devuelve200() throws Exception {
        long id = crearProducto("SKU-STOCK", "Llave inglesa");

        mockMvc.perform(patch("/api/v1/productos/" + id + "/stock")
                        .header("Authorization", "Bearer " + jwtAdmin())
                        .param("stock", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock").value(7));
    }

    @Test
    void actualizarStock_negativo_devuelve400() throws Exception {
        long id = crearProducto("SKU-STOCK-2", "Martillo");

        mockMvc.perform(patch("/api/v1/productos/" + id + "/stock")
                        .header("Authorization", "Bearer " + jwtAdmin())
                        .param("stock", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void actualizarStock_inexistente_devuelve404() throws Exception {
        mockMvc.perform(patch("/api/v1/productos/999999/stock")
                        .header("Authorization", "Bearer " + jwtAdmin())
                        .param("stock", "5"))
                .andExpect(status().isNotFound());
    }

    // ---------- búsqueda vectorial (pgvector) ----------

    @Test
    void buscarPorSimilitud_devuelveProductosDelCatalogo() throws Exception {
        crearProducto("SKU-VEC-1", "Cinta teflon 12m");
        crearProducto("SKU-VEC-2", "Pegamento para PVC");

        mockMvc.perform(get("/api/v1/productos/buscar")
                        .param("q", "material para tuberias")
                        .param("limite", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    // ---------- flujo IA (OpenRouter mockeado) ----------

    @Test
    void diagnose_flujoCompletoConOpenRouterMockeado() throws Exception {
        crearProducto("SKU-TEFLON", "Cinta teflon 12m");
        SugerenciaFerreteriaDTO sugerencia = new SugerenciaFerreteriaDTO(
                List.of("teflon", "cinta"), List.of("llave"), List.of("cinta teflon"));
        when(openRouterService.analizarConsulta("tengo una fuga en una tuberia"))
                .thenReturn(sugerencia);

        mockMvc.perform(post("/api/v1/productos/diagnose")
                        .header("Authorization", "Bearer " + jwtAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"problema\":\"tengo una fuga en una tuberia\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sugerencia.palabrasClave[0]").value("teflon"))
                .andExpect(jsonPath("$.productos.length()").value(1))
                .andExpect(jsonPath("$.productos[0].sku").value("SKU-TEFLON"));
    }

    @Test
    void asistente_devuelveRecomendacionSinAutenticacion() throws Exception {
        SugerenciaFerreteriaDTO sugerencia = new SugerenciaFerreteriaDTO(
                List.of("cinta"), List.of(), List.of());
        when(openRouterService.analizarConsulta("fuga")).thenReturn(sugerencia);

        mockMvc.perform(get("/api/v1/productos/asistente").param("q", "fuga"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sugerencia.palabrasClave[0]").value("cinta"));
    }

    @Test
    void asistente_rateLimit_devuelve429() throws Exception {
        when(openRouterService.analizarConsulta("fuga"))
                .thenThrow(new OpenRouterRateLimitException("rate limit alcanzado"));

        mockMvc.perform(get("/api/v1/productos/asistente").param("q", "fuga"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429));
    }

    @Test
    void asistente_errorDeOpenRouter_devuelve502() throws Exception {
        when(openRouterService.analizarConsulta("fuga"))
                .thenThrow(new OpenRouterException("fallo del modelo"));

        mockMvc.perform(get("/api/v1/productos/asistente").param("q", "fuga"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502));
    }

    // ---------- delete ----------

    @Test
    void eliminarProducto_devuelve204YDespues404() throws Exception {
        long id = crearProducto("SKU-DEL", "Destornillador");

        mockMvc.perform(delete("/api/v1/productos/" + id)
                        .header("Authorization", "Bearer " + jwtAdmin()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/productos/" + id))
                .andExpect(status().isNotFound());
    }
}
