package org.alexis.ecommerceai.controller;

import org.alexis.ecommerceai.dto.PedidoResponseDTO;
import org.alexis.ecommerceai.exception.CarritoVacioException;
import org.alexis.ecommerceai.exception.GlobalExceptionHandler;
import org.alexis.ecommerceai.exception.PedidoNotFoundException;
import org.alexis.ecommerceai.exception.StockInsuficienteException;
import org.alexis.ecommerceai.exception.TransicionEstadoInvalidaException;
import org.alexis.ecommerceai.model.EstadoPedido;
import org.alexis.ecommerceai.service.PedidoService;
import org.alexis.ecommerceai.testconfig.MockMvcContextPathConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PedidoControllerTest {

    @Mock
    private PedidoService pedidoService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        PedidoController controller = new PedidoController(pedidoService);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setValidator(validator)
                .setControllerAdvice(new GlobalExceptionHandler())
                .defaultRequest(get("/").contextPath(MockMvcContextPathConfig.CONTEXT_PATH))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("juan", null,
                        List.of(new SimpleGrantedAuthority("ROLE_CLIENTE"))));
    }

    @AfterEach
    void limpiarSeguridad() {
        SecurityContextHolder.clearContext();
    }

    private static PedidoResponseDTO dto() {
        return new PedidoResponseDTO(100L, "PENDIENTE", new BigDecimal("40.00"), null, List.of());
    }

    @Test
    void crear_conLineasValidas_devuelve201() throws Exception {
        when(pedidoService.create(eq("juan"), any())).thenReturn(dto());

        mockMvc.perform(post("/api/v1/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productoId\":1,\"cantidad\":2}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"));

        verify(pedidoService).create(eq("juan"), any());
    }

    @Test
    void crear_conCantidadCero_devuelve400() throws Exception {
        mockMvc.perform(post("/api/v1/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productoId\":1,\"cantidad\":0}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void crear_conListaVacia_devuelve400() throws Exception {
        mockMvc.perform(post("/api/v1/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void crear_conStockInsuficiente_devuelve400() throws Exception {
        when(pedidoService.create(eq("juan"), any()))
                .thenThrow(new StockInsuficienteException("Stock insuficiente para el producto con id: 1"));

        mockMvc.perform(post("/api/v1/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productoId\":1,\"cantidad\":50}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void listar_devuelve200ConPedidosDelUsuario() throws Exception {
        when(pedidoService.listar("juan")).thenReturn(List.of(dto()));

        mockMvc.perform(get("/api/v1/pedidos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void obtener_pedidoAjeno_devuelve404() throws Exception {
        when(pedidoService.obtener(eq("juan"), eq(false), eq(7L)))
                .thenThrow(new PedidoNotFoundException("Pedido no encontrado con id: 7"));

        mockMvc.perform(get("/api/v1/pedidos/7"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // ---------- /desde-carrito ----------

    @Test
    void crearDesdeCarrito_devuelve201ConElPedidoCreado() throws Exception {
        when(pedidoService.crearDesdeCarrito("juan")).thenReturn(dto());

        mockMvc.perform(post("/api/v1/pedidos/desde-carrito"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"));

        verify(pedidoService).crearDesdeCarrito("juan");
    }

    @Test
    void crearDesdeCarrito_conCarritoVacio_devuelve400() throws Exception {
        when(pedidoService.crearDesdeCarrito("juan"))
                .thenThrow(new CarritoVacioException("El carrito está vacío"));

        mockMvc.perform(post("/api/v1/pedidos/desde-carrito"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    // ---------- transición de estado (ADMIN) ----------

    @Test
    void cambiarEstado_valido_devuelve200() throws Exception {
        when(pedidoService.cambiarEstado(7L, EstadoPedido.CONFIRMADO))
                .thenReturn(new PedidoResponseDTO(7L, "CONFIRMADO", new BigDecimal("40.00"), null, List.of()));

        mockMvc.perform(patch("/api/v1/pedidos/7/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"CONFIRMADO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CONFIRMADO"));
    }

    @Test
    void cambiarEstado_conTransicionInvalida_devuelve409() throws Exception {
        when(pedidoService.cambiarEstado(7L, EstadoPedido.ENTREGADO))
                .thenThrow(new TransicionEstadoInvalidaException("Transición inválida"));

        mockMvc.perform(patch("/api/v1/pedidos/7/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"ENTREGADO\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void cambiarEstado_conEstadoInvalidoEnElCuerpo_devuelve400() throws Exception {
        mockMvc.perform(patch("/api/v1/pedidos/7/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"NO_EXISTE\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cambiarEstado_sinEstado_devuelve400() throws Exception {
        mockMvc.perform(patch("/api/v1/pedidos/7/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.estado").value("El estado es obligatorio"));
    }
}
