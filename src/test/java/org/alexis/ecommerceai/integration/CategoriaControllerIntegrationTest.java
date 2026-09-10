package org.alexis.ecommerceai.integration;

import com.nimbusds.jose.JOSEException;
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

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cobertura de integración del slice de categorías (spec product-categories
 * S1–S4) más la regla de categoria en productos (S5–S7).
 */
@Transactional
class CategoriaControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String SECRETO_TEST = "clave-secreta-test-de-256-bits-para-tests";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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

    private long crearCategoria(String nombre) throws Exception {
        String response = mockMvc.perform(post("/api/v1/categorias")
                        .header("Authorization", "Bearer " + jwtAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"%s\",\"descripcion\":\"Categoria %s\"}".formatted(nombre, nombre)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private long crearProducto(String sku, long categoriaId) throws Exception {
        String body = """
                {"sku":"%s","nombre":"Producto %s","precio":10.50,"stock":5,
                 "descripcionTecnica":"Descripcion tecnica","descripcionColoquial":"el coso",
                 "categoriaId":%d}
                """.formatted(sku, sku, categoriaId);
        String response = mockMvc.perform(post("/api/v1/productos")
                        .header("Authorization", "Bearer " + jwtAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    @Test
    void crudCategoria_flujoCompleto() throws Exception {
        long id = crearCategoria("Fijaciones");

        mockMvc.perform(get("/api/v1/categorias/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Fijaciones"));

        mockMvc.perform(get("/api/v1/categorias"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == 'Fijaciones')].nombre", hasSize(1)));

        mockMvc.perform(put("/api/v1/categorias/" + id)
                        .header("Authorization", "Bearer " + jwtAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Fijaciones\",\"descripcion\":\"Actualizada\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.descripcion").value("Actualizada"));

        mockMvc.perform(delete("/api/v1/categorias/" + id)
                        .header("Authorization", "Bearer " + jwtAdmin()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/categorias/" + id))
                .andExpect(status().isNotFound());
    }

    @Test
    void crearCategoria_nombreDuplicado_devuelve409() throws Exception {
        crearCategoria("Fijaciones");

        mockMvc.perform(post("/api/v1/categorias")
                        .header("Authorization", "Bearer " + jwtAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Fijaciones\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void eliminarCategoria_referenciadaPorProducto_devuelve409YSinCambios() throws Exception {
        long categoriaId = crearCategoria("Pinturas");
        long productoId = crearProducto("SKU-CAT-REF", categoriaId);

        mockMvc.perform(delete("/api/v1/categorias/" + categoriaId)
                        .header("Authorization", "Bearer " + jwtAdmin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        // Ni la categoría ni el producto cambiaron
        mockMvc.perform(get("/api/v1/categorias/" + categoriaId))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/productos/" + productoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoriaId").value(categoriaId));
    }

    @Test
    void crearCategoria_sinToken_devuelve403() throws Exception {
        mockMvc.perform(post("/api/v1/categorias")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"SinAuth\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void listarCategorias_sinToken_esPublico() throws Exception {
        crearCategoria("Publica");

        mockMvc.perform(get("/api/v1/categorias"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == 'Publica')].nombre", hasSize(1)));
    }

    /**
     * La semilla de V2 es parte del contrato: todo producto sin categoría se
     * backfillea a "Sin categoría", así que la fila debe existir siempre.
     */
    @Test
    void semillaSinCategoria_existeComoCategoriaRaiz() throws Exception {
        mockMvc.perform(get("/api/v1/categorias"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == 'Sin categoría')].nombre", hasSize(1)))
                .andExpect(jsonPath("$[?(@.nombre == 'Sin categoría')].padreId[0]").doesNotExist());
    }

    @Test
    void crearProducto_conCategoriaDesconocida_devuelve404() throws Exception {
        mockMvc.perform(post("/api/v1/productos")
                        .header("Authorization", "Bearer " + jwtAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"SKU-CAT-404","nombre":"Fantasma","precio":10.50,"stock":5,
                                 "descripcionTecnica":"t","descripcionColoquial":"c","categoriaId":999999}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
