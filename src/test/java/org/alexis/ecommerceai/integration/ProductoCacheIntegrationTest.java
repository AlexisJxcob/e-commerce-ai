package org.alexis.ecommerceai.integration;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.persistence.EntityManagerFactory;
import org.alexis.ecommerceai.ai.HuggingFaceChatService;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for Caffeine cache behavior on product endpoints.
 * Uses real Caffeine cache (no mocks) and Hibernate statistics to verify
 * that repeated reads are served from cache and that mutations invalidate it.
 *
 * <p>NOT annotated with @Transactional — cache invalidation tests need
 * committed data visible across calls. Each test cleans up via the cache
 * manager and Hibernate stats reset.
 */
class ProductoCacheIntegrationTest extends AbstractIntegrationTest {

    private static final String SECRETO_TEST = "clave-secreta-test-de-256-bits-para-tests";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private CacheManager cacheManager;

    @MockitoBean
    private HuggingFaceChatService huggingFaceChatService;

    private Statistics hibernateStats;

    @BeforeEach
    void setUp() {
        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        sessionFactory.getStatistics().setStatisticsEnabled(true);
        hibernateStats = sessionFactory.getStatistics();
        hibernateStats.clear();
        // Clear all caches before each test
        cacheManager.getCacheNames().forEach(name ->
                Objects.requireNonNull(cacheManager.getCache(name)).clear());
    }

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
        String body = """
                {"nombre":"%s","descripcion":"Categoria de %s"}
                """.formatted(nombre, nombre);
        String response = mockMvc.perform(post("/api/v1/categorias")
                        .header("Authorization", "Bearer " + jwtAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        // Simple extraction — avoid ObjectMapper dependency
        return Long.parseLong(response.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    @Test
    void createInvalidaCacheYSegundoFindAllVaABaseDeDatos() throws Exception {
        // 1. First findAll → hits the database (cold cache)
        mockMvc.perform(get("/api/v1/productos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());

        long queriesAfterFirstCall = hibernateStats.getQueryExecutionCount();
        assertThat(queriesAfterFirstCall).isGreaterThan(0);

        // 2. Second findAll → should be served from cache (no new queries)
        hibernateStats.clear();
        mockMvc.perform(get("/api/v1/productos"))
                .andExpect(status().isOk());

        long queriesAfterCachedCall = hibernateStats.getQueryExecutionCount();
        assertThat(queriesAfterCachedCall)
                .as("Second findAll should hit cache, not database")
                .isEqualTo(0);

        // 3. Create a product → should evict the cache
        long categoriaId = crearCategoria("CAT-CACHE-TEST");
        hibernateStats.clear();

        mockMvc.perform(post("/api/v1/productos")
                        .header("Authorization", "Bearer " + jwtAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"SKU-CACHE","nombre":"Producto Cache","precio":10.50,"stock":5,
                                 "descripcionTecnica":"Tecnica","descripcionColoquial":"el coso",
                                 "categoriaId":%d}
                                """.formatted(categoriaId)))
                .andExpect(status().isCreated());

        // 4. Third findAll → cache was evicted, must hit database again
        hibernateStats.clear();
        mockMvc.perform(get("/api/v1/productos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());

        long queriesAfterEviction = hibernateStats.getQueryExecutionCount();
        assertThat(queriesAfterEviction)
                .as("After create (cache eviction), findAll must hit the database")
                .isGreaterThan(0);
    }

    @Test
    void sizeSuperiorA50_esClampeadoPorResolverGlobal() throws Exception {
        // The global PageableHandlerMethodArgumentResolverCustomizer caps at 50.
        // When requesting size=999, Spring clamps it silently.
        mockMvc.perform(get("/api/v1/productos")
                        .param("size", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(50));
    }
}
