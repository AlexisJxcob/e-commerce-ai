package org.alexis.ecommerceai.integration;

import org.alexis.ecommerceai.testconfig.EmbeddingModelTestConfig;
import org.alexis.ecommerceai.testconfig.InspectorBloqueoCompra;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Compra concurrente de la última unidad, sobre HTTP real (Tomcat + dos hilos).
 *
 * <p>Criterio de aceptación del Bloque 4: "intentos concurrentes sobre la
 * última unidad fallan limpiamente: uno gana, el otro recibe 409, sin
 * stacktraces expuestos". Se ejecuta contra un servidor embebido y no con
 * MockMvc porque la carrera necesita dos transacciones y dos hilos de request
 * de verdad.</p>
 *
 * <p>La concurrencia se hace determinista con
 * {@link InspectorBloqueoCompra}: retiene el primer {@code UPDATE productos}
 * hasta que ambas peticiones hayan leído el stock, garantizando que la
 * perdedora pierde por bloqueo optimista (409) y no por llegar tarde (400).</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
@Import(EmbeddingModelTestConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CompraConcurrenteIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
            .withDatabaseName("ecommerce_concurrencia_test")
            .withUsername("test")
            .withPassword("test")
            .withInitScript("pgvector-init.sql");

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.session_factory.statement_inspector",
                () -> InspectorBloqueoCompra.class.getName());
    }

    @LocalServerPort
    private int puerto;

    @Autowired
    private ObjectMapper objectMapper;

    private RestClient http;

    @BeforeEach
    void setUp() {
        http = RestClient.builder()
                .baseUrl("http://localhost:" + puerto + "/api")
                .build();
    }

    @AfterEach
    void tearDown() {
        InspectorBloqueoCompra.desarmar();
    }

    private record Respuesta(int status, String cuerpo) {
    }

    private Respuesta peticion(String metodo, String uri, String token, String json) {
        RestClient.RequestHeadersSpec<?> spec = "POST".equals(metodo)
                ? http.post().uri(uri)
                : http.get().uri(uri);
        if (token != null) {
            spec = spec.header("Authorization", "Bearer " + token);
        }
        if (json != null) {
            spec = ((RestClient.RequestBodySpec) spec).contentType(MediaType.APPLICATION_JSON).body(json);
        }
        return spec.exchange((request, response) -> new Respuesta(
                response.getStatusCode().value(),
                new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8)));
    }

    private String tokenDe(String username, String password) {
        Respuesta r = peticion("POST", "/auth/login", null,
                "{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password));
        assertThat(r.status()).as("login de %s", username).isEqualTo(200);
        return objectMapper.readTree(r.cuerpo()).get("token").asText();
    }

    @Test
    void dosComprasSimultaneasDeLaUltimaUnidad_unaGanaYLaOtraRecibe409() throws Exception {
        String tokenAdmin = tokenDe("admin", "admin123");
        peticion("POST", "/auth/register", null,
                "{\"username\":\"comprador\",\"password\":\"secreta123\"}");
        String tokenComprador = tokenDe("comprador", "secreta123");

        long categoriaId = objectMapper.readTree(peticion("POST", "/v1/categorias", tokenAdmin,
                        "{\"nombre\":\"CAT-CONC\"}").cuerpo()).get("id").asLong();
        long productoId = objectMapper.readTree(peticion("POST", "/v1/productos", tokenAdmin, """
                {"sku":"SKU-CONC-1","nombre":"Última unidad","precio":10.00,"stock":1,
                 "descripcionTecnica":"t","descripcionColoquial":"c","categoriaId":%d}
                """.formatted(categoriaId)).cuerpo()).get("id").asLong();

        String cuerpoPedido = "{\"items\":[{\"productoId\":%d,\"cantidad\":1}]}".formatted(productoId);
        CyclicBarrier salida = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Respuesta> intento = () -> {
                salida.await(10, TimeUnit.SECONDS);
                return peticion("POST", "/v1/pedidos", tokenComprador, cuerpoPedido);
            };

            InspectorBloqueoCompra.armar();
            Future<Respuesta> primero = pool.submit(intento);
            Future<Respuesta> segundo = pool.submit(intento);
            Respuesta a = primero.get(30, TimeUnit.SECONDS);
            Respuesta b = segundo.get(30, TimeUnit.SECONDS);
            InspectorBloqueoCompra.desarmar();

            assertThat(List.of(a.status(), b.status()))
                    .as("uno gana (201) y el otro pierde por bloqueo optimista (409)")
                    .containsExactlyInAnyOrder(201, 409);

            Respuesta perdedora = a.status() == 409 ? a : b;
            assertThat(perdedora.cuerpo())
                    .as("el error sale como ErrorResponse, sin stacktrace ni detalle interno")
                    .contains("\"status\":409")
                    .doesNotContain("Exception")
                    .doesNotContain("org.alexis")
                    .doesNotContain("at ")
                    .doesNotContain("Hibernate");
        } finally {
            pool.shutdownNow();
        }

        // Estado final consistente: un solo pedido, stock agotado (nunca negativo)
        Respuesta pedidos = peticion("GET", "/v1/pedidos", tokenComprador, null);
        assertThat(objectMapper.readTree(pedidos.cuerpo())).hasSize(1);

        Respuesta producto = peticion("GET", "/v1/productos/" + productoId, null, null);
        var nodo = objectMapper.readTree(producto.cuerpo());
        assertThat(nodo.get("stock").asInt()).isZero();
    }

    @Test
    void compraSecuencial_cuandoElStockYaNoAlcanza_devuelve400YNoCreaPedido() {
        String tokenAdmin = tokenDe("admin", "admin123");
        peticion("POST", "/auth/register", null,
                "{\"username\":\"tarde\",\"password\":\"secreta123\"}");
        String tokenTarde = tokenDe("tarde", "secreta123");

        long categoriaId = objectMapper.readTree(peticion("POST", "/v1/categorias", tokenAdmin,
                "{\"nombre\":\"CAT-CONC-2\"}").cuerpo()).get("id").asLong();
        long productoId = objectMapper.readTree(peticion("POST", "/v1/productos", tokenAdmin, """
                {"sku":"SKU-CONC-2","nombre":"Poco stock","precio":10.00,"stock":2,
                 "descripcionTecnica":"t","descripcionColoquial":"c","categoriaId":%d}
                """.formatted(categoriaId)).cuerpo()).get("id").asLong();

        String compra = "{\"items\":[{\"productoId\":%d,\"cantidad\":2}]}".formatted(productoId);
        assertThat(peticion("POST", "/v1/pedidos", tokenTarde, compra).status()).isEqualTo(201);

        // Segunda compra sin stock disponible: 400 (petición imposible), sin pedido parcial
        Respuesta segunda = peticion("POST", "/v1/pedidos", tokenTarde, compra);
        assertThat(segunda.status()).isEqualTo(400);
        assertThat(segunda.cuerpo()).contains("Stock insuficiente");

        Respuesta pedidos = peticion("GET", "/v1/pedidos", tokenTarde, null);
        assertThat(objectMapper.readTree(pedidos.cuerpo())).hasSize(1);
    }

    @Test
    void crearPedidoDesdeCarrito_vaciaElCarritoYDescuentaStock() {
        String tokenAdmin = tokenDe("admin", "admin123");
        peticion("POST", "/auth/register", null,
                "{\"username\":\"carrito\",\"password\":\"secreta123\"}");
        String token = tokenDe("carrito", "secreta123");

        long categoriaId = objectMapper.readTree(peticion("POST", "/v1/categorias", tokenAdmin,
                "{\"nombre\":\"CAT-CONC-3\"}").cuerpo()).get("id").asLong();
        long productoId = objectMapper.readTree(peticion("POST", "/v1/productos", tokenAdmin, """
                {"sku":"SKU-CONC-3","nombre":"Del carrito","precio":25.00,"stock":5,
                 "descripcionTecnica":"t","descripcionColoquial":"c","categoriaId":%d}
                """.formatted(categoriaId)).cuerpo()).get("id").asLong();

        assertThat(peticion("POST", "/v1/carrito/items", token,
                "{\"productoId\":%d,\"cantidad\":2}".formatted(productoId)).status()).isEqualTo(200);

        Respuesta pedido = peticion("POST", "/v1/pedidos/desde-carrito", token, null);
        assertThat(pedido.status()).isEqualTo(201);
        var nodo = objectMapper.readTree(pedido.cuerpo());
        assertThat(nodo.get("estado").asText()).isEqualTo("PENDIENTE");
        assertThat(nodo.get("total").decimalValue()).isEqualByComparingTo(new BigDecimal("50.00"));

        // El carrito quedó vacío y el stock descontado
        assertThat(objectMapper.readTree(peticion("GET", "/v1/carrito", token, null).cuerpo())
                .get("items")).isEmpty();
        assertThat(objectMapper.readTree(peticion("GET", "/v1/productos/" + productoId, null, null).cuerpo())
                .get("stock").asInt()).isEqualTo(3);
    }
}
