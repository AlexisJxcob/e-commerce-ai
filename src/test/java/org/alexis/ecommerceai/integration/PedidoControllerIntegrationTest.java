package org.alexis.ecommerceai.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cobertura de integración del slice de pedidos (spec order-management):
 * snapshot, decremento transaccional, privacidad por usuario y protección
 * del catálogo. La condición de carrera se verifica a nivel unitario
 * (optimistic-lock → 409).
 */
@Transactional
class PedidoControllerIntegrationTest extends AbstractIntegrationTest {

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

    private long crearPedido(String token, String body) throws Exception {
        String response = mockMvc.perform(post("/api/v1/pedidos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private int stockDe(long productoId) throws Exception {
        String response = mockMvc.perform(get("/api/v1/productos/" + productoId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("stock").asInt();
    }

    @Test
    void crearPedido_valido_decrementaStockYGuardaSnapshot() throws Exception {
        registrar("juan");
        String tokenAdmin = login("admin", "admin123");
        String tokenJuan = login("juan", "secreta123");
        long categoriaId = crearCategoria("CAT-PED-1", tokenAdmin);
        long p1 = crearProducto("SKU-PED-1", 5, 10.00, categoriaId, tokenAdmin);
        long p2 = crearProducto("SKU-PED-2", 3, 20.00, categoriaId, tokenAdmin);

        String body = "{\"items\":[{\"productoId\":%d,\"cantidad\":2},{\"productoId\":%d,\"cantidad\":1}]}"
                .formatted(p1, p2);
        mockMvc.perform(post("/api/v1/pedidos")
                        .header("Authorization", "Bearer " + tokenJuan)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.total").value(40.0))
                .andExpect(jsonPath("$.items.length()").value(2));

        assert stockDe(p1) == 3;
        assert stockDe(p2) == 2;
    }

    @Test
    void crearPedido_stockInsuficiente_400SinPedidoNiCambios() throws Exception {
        registrar("juan");
        String tokenAdmin = login("admin", "admin123");
        String tokenJuan = login("juan", "secreta123");
        long categoriaId = crearCategoria("CAT-PED-2", tokenAdmin);
        long p1 = crearProducto("SKU-PED-3", 1, 10.00, categoriaId, tokenAdmin);

        // Stock insuficiente ya en la petición → 400 (petición imposible),
        // no 409 (ese código queda para la carrera por la última unidad).
        mockMvc.perform(post("/api/v1/pedidos")
                        .header("Authorization", "Bearer " + tokenJuan)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productoId\":%d,\"cantidad\":5}]}".formatted(p1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Stock insuficiente")));

        mockMvc.perform(get("/api/v1/pedidos")
                        .header("Authorization", "Bearer " + tokenJuan))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        assert stockDe(p1) == 1;
    }

    @Test
    void crearPedido_validacionYLookup() throws Exception {
        registrar("juan");
        String tokenAdmin = login("admin", "admin123");
        String tokenJuan = login("juan", "secreta123");
        long categoriaId = crearCategoria("CAT-PED-3", tokenAdmin);
        long p1 = crearProducto("SKU-PED-4", 5, 10.00, categoriaId, tokenAdmin);

        mockMvc.perform(post("/api/v1/pedidos")
                        .header("Authorization", "Bearer " + tokenJuan)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[]}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/pedidos")
                        .header("Authorization", "Bearer " + tokenJuan)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productoId\":%d,\"cantidad\":0}]}".formatted(p1)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/pedidos")
                        .header("Authorization", "Bearer " + tokenJuan)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productoId\":999999,\"cantidad\":1}]}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void pedidoAjeno_comoCliente_404ComoAdmin_200() throws Exception {
        registrar("juan");
        registrar("pedro");
        String tokenAdmin = login("admin", "admin123");
        String tokenJuan = login("juan", "secreta123");
        String tokenPedro = login("pedro", "secreta123");
        long categoriaId = crearCategoria("CAT-PED-4", tokenAdmin);
        long p1 = crearProducto("SKU-PED-5", 5, 10.00, categoriaId, tokenAdmin);
        long pedidoId = crearPedido(tokenJuan, "{\"items\":[{\"productoId\":%d,\"cantidad\":1}]}".formatted(p1));

        mockMvc.perform(get("/api/v1/pedidos/" + pedidoId)
                        .header("Authorization", "Bearer " + tokenPedro))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/pedidos/" + pedidoId)
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk());

        // Cada usuario solo lista lo propio
        mockMvc.perform(get("/api/v1/pedidos")
                        .header("Authorization", "Bearer " + tokenPedro))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/v1/pedidos")
                        .header("Authorization", "Bearer " + tokenJuan))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void pedidos_sinToken_403() throws Exception {
        mockMvc.perform(get("/api/v1/pedidos"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productoId\":1,\"cantidad\":1}]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void cambioDePrecio_noAlteraPedidoExistente() throws Exception {
        registrar("juan");
        String tokenAdmin = login("admin", "admin123");
        String tokenJuan = login("juan", "secreta123");
        long categoriaId = crearCategoria("CAT-PED-5", tokenAdmin);
        long p1 = crearProducto("SKU-PED-6", 5, 10.00, categoriaId, tokenAdmin);
        long pedidoId = crearPedido(tokenJuan, "{\"items\":[{\"productoId\":%d,\"cantidad\":2}]}".formatted(p1));

        mockMvc.perform(put("/api/v1/productos/" + p1)
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"SKU-PED-6","nombre":"Producto SKU-PED-6","precio":99.99,"stock":3,
                                 "descripcionTecnica":"t","descripcionColoquial":"c","categoriaId":%d}
                                """.formatted(categoriaId)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/pedidos/" + pedidoId)
                        .header("Authorization", "Bearer " + tokenJuan))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(20.0))
                .andExpect(jsonPath("$.items[0].precioUnitario").value(10.0));
    }

    @Test
    void eliminarProducto_conPedidos_409() throws Exception {
        registrar("juan");
        String tokenAdmin = login("admin", "admin123");
        String tokenJuan = login("juan", "secreta123");
        long categoriaId = crearCategoria("CAT-PED-6", tokenAdmin);
        long p1 = crearProducto("SKU-PED-7", 5, 10.00, categoriaId, tokenAdmin);
        crearPedido(tokenJuan, "{\"items\":[{\"productoId\":%d,\"cantidad\":1}]}".formatted(p1));

        mockMvc.perform(delete("/api/v1/productos/" + p1)
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }
}
