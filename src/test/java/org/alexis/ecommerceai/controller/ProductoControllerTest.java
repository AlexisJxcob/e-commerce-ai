package org.alexis.ecommerceai.controller;

import org.alexis.ecommerceai.ai.AsistenteIAService;
import org.alexis.ecommerceai.dto.BusquedaInteligenteResponse;
import org.alexis.ecommerceai.dto.ProductoResponseDTO;
import org.alexis.ecommerceai.dto.SugerenciaFerreteriaDTO;
import org.alexis.ecommerceai.exception.GlobalExceptionHandler;
import org.alexis.ecommerceai.exception.ProductoNotFoundException;
import org.alexis.ecommerceai.exception.StockUpdateConflictException;
import org.alexis.ecommerceai.service.ProductoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProductoControllerTest {

    @Mock
    private ProductoService productoService;

    @Mock
    private AsistenteIAService asistenteIAService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ProductoController controller = new ProductoController(productoService, asistenteIAService);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setValidator(validator)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static ProductoResponseDTO dto(Long id, String sku) {
        return new ProductoResponseDTO(id, sku, "Cinta", "tec", "col",
                new BigDecimal("10.00"), 5);
    }

    // ---------- lecturas públicas ----------

    @Test
    void listar_devuelve200ConListaDeProductos() throws Exception {
        when(productoService.findAll()).thenReturn(List.of(dto(1L, "SKU-1"), dto(2L, "SKU-2")));

        mockMvc.perform(get("/api/v1/productos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].sku").value("SKU-1"));
    }

    @Test
    void obtener_devuelve200ConProducto() throws Exception {
        when(productoService.findById(1L)).thenReturn(dto(1L, "SKU-1"));

        mockMvc.perform(get("/api/v1/productos/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.nombre").value("Cinta"));
    }

    @Test
    void obtener_devuelve404CuandoNoExiste() throws Exception {
        when(productoService.findById(999L))
                .thenThrow(new ProductoNotFoundException("Producto no encontrado con id: 999"));

        mockMvc.perform(get("/api/v1/productos/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Producto no encontrado con id: 999"));
    }

    @Test
    void buscarPorSimilitud_devuelve200() throws Exception {
        when(productoService.buscarPorSimilitud("pegamento", 5))
                .thenReturn(List.of(dto(1L, "SKU-1")));

        mockMvc.perform(get("/api/v1/productos/buscar").param("q", "pegamento"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    // ---------- escritura (validación) ----------

    @Test
    void crear_conBodyValido_devuelve201() throws Exception {
        when(productoService.create(any())).thenReturn(dto(1L, "SKU-1"));

        mockMvc.perform(post("/api/v1/productos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"SKU-1","nombre":"Cinta","precio":10.50,"stock":5,
                                 "descripcionTecnica":"tec","descripcionColoquial":"col"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.sku").value("SKU-1"));
    }

    @Test
    void crear_conBodyInvalido_devuelve400ConErroresDeCampo() throws Exception {
        mockMvc.perform(post("/api/v1/productos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"","nombre":"","precio":-1,"stock":-1,
                                 "descripcionTecnica":"","descripcionColoquial":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors").isMap())
                .andExpect(jsonPath("$.fieldErrors.sku").exists())
                .andExpect(jsonPath("$.fieldErrors.precio").exists());
    }

    @Test
    void diagnosticar_devuelve200ConRecomendacion() throws Exception {
        SugerenciaFerreteriaDTO sugerencia = new SugerenciaFerreteriaDTO(
                List.of("cinta"), List.of("llave"), List.of());
        when(asistenteIAService.buscarRecomendacion("tengo una fuga"))
                .thenReturn(new BusquedaInteligenteResponse(sugerencia, List.of(dto(1L, "SKU-1"))));

        mockMvc.perform(post("/api/v1/productos/diagnose")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"problema\":\"tengo una fuga\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sugerencia.palabrasClave[0]").value("cinta"))
                .andExpect(jsonPath("$.productos.length()").value(1));
    }

    @Test
    void consultarAsistente_devuelve200() throws Exception {
        SugerenciaFerreteriaDTO sugerencia = new SugerenciaFerreteriaDTO(
                List.of(), List.of(), List.of());
        when(asistenteIAService.buscarRecomendacion("fuga")).thenReturn(
                new BusquedaInteligenteResponse(sugerencia, List.of()));

        mockMvc.perform(get("/api/v1/productos/asistente").param("q", "fuga"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productos").isEmpty());
    }

    // ---------- stock ----------

    @Test
    void actualizarStock_devuelve200() throws Exception {
        when(productoService.updateStock(eq(1L), eq(10))).thenReturn(dto(1L, "SKU-1"));

        mockMvc.perform(patch("/api/v1/productos/1/stock").param("stock", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock").value(5));
    }

    @Test
    void actualizarStock_conConflicto_devuelve409() throws Exception {
        doThrow(new StockUpdateConflictException(
                "Conflicto de concurrencia al actualizar stock. Intente nuevamente."))
                .when(productoService).updateStock(anyLong(), anyInt());

        mockMvc.perform(patch("/api/v1/productos/1/stock").param("stock", "10"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    // ---------- delete ----------

    @Test
    void eliminar_devuelve204() throws Exception {
        doNothing().when(productoService).delete(1L);

        mockMvc.perform(delete("/api/v1/productos/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void eliminar_devuelve404CuandoNoExiste() throws Exception {
        doThrow(new ProductoNotFoundException("Producto no encontrado con id: 999"))
                .when(productoService).delete(999L);

        mockMvc.perform(delete("/api/v1/productos/999"))
                .andExpect(status().isNotFound());
    }
}
