package org.alexis.ecommerceai.exception;

import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifica que cada excepción de dominio se traduzca al código HTTP correcto
 * a través de {@link GlobalExceptionHandler}.
 */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @RestController
    static class StubController {
        @GetMapping("/nfe")
        public void notFound() {
            throw new ProductoNotFoundException("producto no encontrado");
        }

        @GetMapping("/sce")
        public void stockConflict() {
            throw new StockUpdateConflictException("conflicto de stock");
        }

        @GetMapping("/rle")
        public void rateLimit() {
            throw new OpenRouterRateLimitException("rate limit alcanzado");
        }

        @GetMapping("/ore")
        public void openRouterError() {
            throw new OpenRouterException("error de openrouter", 503);
        }

        @GetMapping("/ore-2xx")
        public void openRouterError2xx() {
            throw new OpenRouterException("estado anomalo", 200);
        }

        @GetMapping("/cve")
        public void constraintViolation() {
            throw new ConstraintViolationException("violacion de parametro", Set.of());
        }

        @GetMapping("/mismatch")
        public void typeMismatch() {
            throw mock(MethodArgumentTypeMismatchException.class);
        }

        @GetMapping("/unreadable")
        public void unreadableBody() {
            throw mock(HttpMessageNotReadableException.class);
        }

        @GetMapping("/missing-param")
        public void missingParam() {
            throw mock(MissingServletRequestParameterException.class);
        }

        @GetMapping("/gen")
        public void generic() {
            throw new IllegalStateException("boom");
        }
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new StubController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void productoNotFound_devuelve404() throws Exception {
        mockMvc.perform(get("/nfe"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("producto no encontrado"));
    }

    @Test
    void stockConflict_devuelve409() throws Exception {
        mockMvc.perform(get("/sce"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void rateLimit_devuelve429() throws Exception {
        mockMvc.perform(get("/rle"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429));
    }

    @Test
    void openRouterError_devuelveElStatusDeLaExcepcion() throws Exception {
        mockMvc.perform(get("/ore"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));
    }

    @Test
    void openRouterError_conStatus2xxSeCoercionaA502() throws Exception {
        mockMvc.perform(get("/ore-2xx"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502));
    }

    @Test
    void constraintViolation_devuelve400() throws Exception {
        mockMvc.perform(get("/cve"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void typeMismatch_devuelve400() throws Exception {
        mockMvc.perform(get("/mismatch"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void bodyInvalido_devuelve400() throws Exception {
        mockMvc.perform(get("/unreadable"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void parametroFaltante_devuelve400() throws Exception {
        mockMvc.perform(get("/missing-param"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void errorGenerico_devuelve500ConMensajeNeutral() throws Exception {
        mockMvc.perform(get("/gen"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("Error interno del servidor"));
    }
}
