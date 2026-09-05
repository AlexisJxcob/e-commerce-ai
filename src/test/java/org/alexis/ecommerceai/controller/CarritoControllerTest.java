package org.alexis.ecommerceai.controller;

import org.alexis.ecommerceai.dto.CarritoResponseDTO;
import org.alexis.ecommerceai.dto.LineaCarritoResponseDTO;
import org.alexis.ecommerceai.exception.GlobalExceptionHandler;
import org.alexis.ecommerceai.exception.ItemCarritoNotFoundException;
import org.alexis.ecommerceai.exception.ProductoNotFoundException;
import org.alexis.ecommerceai.service.CarritoService;
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

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
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
class CarritoControllerTest {

    @Mock
    private CarritoService carritoService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        CarritoController controller = new CarritoController(carritoService);
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

    private static LineaCarritoResponseDTO linea() {
        return new LineaCarritoResponseDTO(10L, 1L, "Producto 1",
                new BigDecimal("10.00"), 2, new BigDecimal("20.00"));
    }

    @Test
    void ver_devuelve200ConCarritoDelUsuario() throws Exception {
        when(carritoService.ver("juan"))
                .thenReturn(new CarritoResponseDTO(List.of(linea()), new BigDecimal("20.00")));

        mockMvc.perform(get("/api/v1/carrito"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.total").value(20.0));
    }

    @Test
    void agregar_conBodyValido_devuelve200ConLinea() throws Exception {
        when(carritoService.agregar(eq("juan"), eq(1L), eq(2))).thenReturn(linea());

        mockMvc.perform(post("/api/v1/carrito/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productoId\":1,\"cantidad\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productoId").value(1))
                .andExpect(jsonPath("$.cantidad").value(2));
    }

    @Test
    void agregar_conCantidadCero_devuelve400() throws Exception {
        mockMvc.perform(post("/api/v1/carrito/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productoId\":1,\"cantidad\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void agregar_conProductoDesconocido_devuelve404() throws Exception {
        when(carritoService.agregar(anyString(), anyLong(), anyInt()))
                .thenThrow(new ProductoNotFoundException("Producto no encontrado con id: 999"));

        mockMvc.perform(post("/api/v1/carrito/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productoId\":999,\"cantidad\":1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void actualizarCantidad_devuelve200() throws Exception {
        when(carritoService.actualizarCantidad(eq("juan"), eq(10L), eq(5))).thenReturn(linea());

        mockMvc.perform(patch("/api/v1/carrito/items/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cantidad\":5}"))
                .andExpect(status().isOk());
    }

    @Test
    void actualizarCantidad_lineaAjena_devuelve404() throws Exception {
        when(carritoService.actualizarCantidad(eq("juan"), eq(10L), eq(5)))
                .thenThrow(new ItemCarritoNotFoundException("Línea de carrito no encontrada con id: 10"));

        mockMvc.perform(patch("/api/v1/carrito/items/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cantidad\":5}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void eliminarLinea_devuelve204() throws Exception {
        doNothing().when(carritoService).eliminarLinea("juan", 10L);

        mockMvc.perform(delete("/api/v1/carrito/items/10"))
                .andExpect(status().isNoContent());
    }

    @Test
    void eliminarLinea_ajena_devuelve404() throws Exception {
        doThrow(new ItemCarritoNotFoundException("Línea de carrito no encontrada con id: 10"))
                .when(carritoService).eliminarLinea("juan", 10L);

        mockMvc.perform(delete("/api/v1/carrito/items/10"))
                .andExpect(status().isNotFound());
    }

    @Test
    void vaciar_devuelve204() throws Exception {
        doNothing().when(carritoService).vaciar("juan");

        mockMvc.perform(delete("/api/v1/carrito"))
                .andExpect(status().isNoContent());
    }
}
