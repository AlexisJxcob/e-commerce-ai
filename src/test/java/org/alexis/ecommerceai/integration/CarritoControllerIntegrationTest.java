package org.alexis.ecommerceai.integration;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cobertura de integración del carrito persistente (spec shopping-cart S1–S9).
 */
@Transactional
class CarritoControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

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

    private long crearCategoria(String nombre, String tokenAdmin) throws Exception {
        String response = mockMvc.perform(post("/api/v1/categorias")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"%s\"}".formatted(nombre)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private long crearProducto(String sku, int stock, double precio, long categoriaId, String tokenAdmin)
            throws Exception {
        String body = """
                {"sku":"%s","nombre":"Producto %s","precio":%s,"stock":%d,
                 "descripcionTecnica":"t","descripcionColoquial":"c","categoriaId":%d}
                """.formatted(sku, sku, precio, stock, categoriaId);
        String response = mockMvc.perform(post("/api/v1/productos")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private long agregarAlCarrito(String token, long productoId, int cantidad) throws Exception {
        String response = mockMvc.perform(post("/api/v1/carrito/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productoId\":%d,\"cantidad\":%d}".formatted(productoId, cantidad)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    @Test
    void carrito_sinToken_403() throws Exception {
        mockMvc.perform(get("/api/v1/carrito"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/carrito/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productoId\":1,\"cantidad\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void agregar_dosProductosDistintos_dosLineas() throws Exception {
        registrar("juan");
        String tokenAdmin = login("admin", "admin123");
        String tokenJuan = login("juan", "secreta123");
        long categoriaId = crearCategoria("CAT-CAR-1", tokenAdmin);
        long p1 = crearProducto("SKU-CAR-1", 5, 10.00, categoriaId, tokenAdmin);
        long p2 = crearProducto("SKU-CAR-2", 5, 20.00, categoriaId, tokenAdmin);

        agregarAlCarrito(tokenJuan, p1, 1);
        agregarAlCarrito(tokenJuan, p2, 1);

        mockMvc.perform(get("/api/v1/carrito")
                        .header("Authorization", "Bearer " + tokenJuan))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.total").value(30.0));
    }

    @Test
    void reagregar_mismoProducto_fusionaEnUnaLinea() throws Exception {
        registrar("juan");
        String tokenAdmin = login("admin", "admin123");
        String tokenJuan = login("juan", "secreta123");
        long categoriaId = crearCategoria("CAT-CAR-2", tokenAdmin);
        long p1 = crearProducto("SKU-CAR-3", 10, 10.00, categoriaId, tokenAdmin);

        agregarAlCarrito(tokenJuan, p1, 2);
        agregarAlCarrito(tokenJuan, p1, 3);

        mockMvc.perform(get("/api/v1/carrito")
                        .header("Authorization", "Bearer " + tokenJuan))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].cantidad").value(5))
                .andExpect(jsonPath("$.total").value(50.0));
    }

    @Test
    void agregar_cantidadCeroYProductoDesconocido_400Y404() throws Exception {
        registrar("juan");
        String tokenJuan = login("juan", "secreta123");

        mockMvc.perform(post("/api/v1/carrito/items")
                        .header("Authorization", "Bearer " + tokenJuan)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productoId\":1,\"cantidad\":0}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/carrito/items")
                        .header("Authorization", "Bearer " + tokenJuan)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productoId\":999999,\"cantidad\":1}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void lineaAjena_operarDevuelve404() throws Exception {
        registrar("juan");
        registrar("pedro");
        String tokenAdmin = login("admin", "admin123");
        String tokenJuan = login("juan", "secreta123");
        String tokenPedro = login("pedro", "secreta123");
        long categoriaId = crearCategoria("CAT-CAR-3", tokenAdmin);
        long p1 = crearProducto("SKU-CAR-4", 5, 10.00, categoriaId, tokenAdmin);
        long lineaId = agregarAlCarrito(tokenJuan, p1, 1);

        mockMvc.perform(patch("/api/v1/carrito/items/" + lineaId)
                        .header("Authorization", "Bearer " + tokenPedro)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cantidad\":9}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/carrito/items/" + lineaId)
                        .header("Authorization", "Bearer " + tokenPedro))
                .andExpect(status().isNotFound());

        // La línea de Juan sigue intacta
        mockMvc.perform(get("/api/v1/carrito")
                        .header("Authorization", "Bearer " + tokenJuan))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void patch_seteaCantidadYDelete_vaciaLinea() throws Exception {
        registrar("juan");
        String tokenAdmin = login("admin", "admin123");
        String tokenJuan = login("juan", "secreta123");
        long categoriaId = crearCategoria("CAT-CAR-4", tokenAdmin);
        long p1 = crearProducto("SKU-CAR-5", 5, 10.00, categoriaId, tokenAdmin);
        long lineaId = agregarAlCarrito(tokenJuan, p1, 1);

        mockMvc.perform(patch("/api/v1/carrito/items/" + lineaId)
                        .header("Authorization", "Bearer " + tokenJuan)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cantidad\":4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cantidad").value(4));

        mockMvc.perform(delete("/api/v1/carrito/items/" + lineaId)
                        .header("Authorization", "Bearer " + tokenJuan))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/carrito")
                        .header("Authorization", "Bearer " + tokenJuan))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.total").value(0.0));
    }

    @Test
    void agregar_sobreStock_aceptado() throws Exception {
        registrar("juan");
        String tokenAdmin = login("admin", "admin123");
        String tokenJuan = login("juan", "secreta123");
        long categoriaId = crearCategoria("CAT-CAR-5", tokenAdmin);
        long p1 = crearProducto("SKU-CAR-6", 1, 10.00, categoriaId, tokenAdmin);

        agregarAlCarrito(tokenJuan, p1, 5);

        // ...pero el pedido posterior falla con 409
        mockMvc.perform(post("/api/v1/pedidos")
                        .header("Authorization", "Bearer " + tokenJuan)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productoId\":%d,\"cantidad\":5}]}".formatted(p1)))
                .andExpect(status().isConflict());
    }

    @Test
    void productoBorrado_lineaExcluidaDelCarrito() throws Exception {
        registrar("juan");
        String tokenAdmin = login("admin", "admin123");
        String tokenJuan = login("juan", "secreta123");
        long categoriaId = crearCategoria("CAT-CAR-6", tokenAdmin);
        long p1 = crearProducto("SKU-CAR-7", 5, 10.00, categoriaId, tokenAdmin);
        agregarAlCarrito(tokenJuan, p1, 2);

        mockMvc.perform(delete("/api/v1/productos/" + p1)
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isNoContent());

        // Frontera real entre requests: en producción cada request corre en su
        // propia transacción (el DELETE ya hizo commit y la cascada borró la
        // línea). Este test comparte una sola transacción, así que se fuerza
        // el flush+clear para simular esa frontera.
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/v1/carrito")
                        .header("Authorization", "Bearer " + tokenJuan))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.total").value(0.0));
    }

    @Test
    void crearPedido_noAlteraElCarrito() throws Exception {
        registrar("juan");
        String tokenAdmin = login("admin", "admin123");
        String tokenJuan = login("juan", "secreta123");
        long categoriaId = crearCategoria("CAT-CAR-7", tokenAdmin);
        long p1 = crearProducto("SKU-CAR-8", 5, 10.00, categoriaId, tokenAdmin);
        agregarAlCarrito(tokenJuan, p1, 1);

        mockMvc.perform(post("/api/v1/pedidos")
                        .header("Authorization", "Bearer " + tokenJuan)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productoId\":%d,\"cantidad\":1}]}".formatted(p1)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/carrito")
                        .header("Authorization", "Bearer " + tokenJuan))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1));

        mockMvc.perform(delete("/api/v1/carrito")
                        .header("Authorization", "Bearer " + tokenJuan))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/carrito")
                        .header("Authorization", "Bearer " + tokenJuan))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }
}
