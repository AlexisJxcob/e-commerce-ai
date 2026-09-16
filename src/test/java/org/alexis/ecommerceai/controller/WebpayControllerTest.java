package org.alexis.ecommerceai.controller;

import org.alexis.ecommerceai.config.WebpayProperties;
import org.alexis.ecommerceai.dto.webpay.WebpayCommitResponseDTO;
import org.alexis.ecommerceai.service.WebpayService;
import org.alexis.ecommerceai.testconfig.MockMvcContextPathConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WebpayControllerTest {

    @Mock
    private WebpayService webpayService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        WebpayProperties properties = new WebpayProperties(
                "597055555532",
                "579B532A7440BB0C9079DED94D31EA1615BACEB56610332264630D42D0A36B1C",
                "https://webpay3gint.transbank.cl",
                "http://localhost:8080/api/v1/pagos/webpay/retorno",
                "http://localhost:3000/pedidos"
        );
        WebpayController controller = new WebpayController(webpayService, properties);
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .defaultRequest(get("/").contextPath(MockMvcContextPathConfig.CONTEXT_PATH))
                .build();
    }

    @Test
    void retornoWebpay_conTokenWsAprobado_redirigeAExito() throws Exception {
        WebpayCommitResponseDTO commitResponse = new WebpayCommitResponseDTO(
                "TSY", 10000.0, "AUTHORIZED", "PEDIDO-1", "SESION-1",
                new WebpayCommitResponseDTO.CardDetail("6670"),
                "0916", "2026-09-16T12:00:00Z", "AUTH123", "VD", 0, null, null, null
        );
        when(webpayService.confirmarTransaccion("token_123")).thenReturn(commitResponse);

        mockMvc.perform(post("/api/v1/pagos/webpay/retorno")
                        .param("token_ws", "token_123"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location",
                        "http://localhost:3000/pedidos?token=token_123&status=exito&buy_order=PEDIDO-1"));
    }

    @Test
    void retornoWebpay_conTokenWsRechazado_redirigeARechazado() throws Exception {
        WebpayCommitResponseDTO commitResponse = new WebpayCommitResponseDTO(
                "TSN", 10000.0, "FAILED", "PEDIDO-1", "SESION-1",
                new WebpayCommitResponseDTO.CardDetail("6670"),
                "0916", "2026-09-16T12:00:00Z", null, "VD", -1, null, null, null
        );
        when(webpayService.confirmarTransaccion("token_123")).thenReturn(commitResponse);

        mockMvc.perform(get("/api/v1/pagos/webpay/retorno")
                        .param("token_ws", "token_123"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location",
                        "http://localhost:3000/pedidos?token=token_123&status=rechazado&buy_order=PEDIDO-1"));
    }

    @Test
    void retornoWebpay_conTbkToken_registraCancelacionYRedirigeACancelado() throws Exception {
        mockMvc.perform(post("/api/v1/pagos/webpay/retorno")
                        .param("TBK_TOKEN", "tbk_token_xyz"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location",
                        "http://localhost:3000/pedidos?token=tbk_token_xyz&status=cancelado"));

        verify(webpayService).registrarCancelacion("tbk_token_xyz");
    }

    @Test
    void retornoWebpay_sinParametros_redirigeAInvalido() throws Exception {
        mockMvc.perform(get("/api/v1/pagos/webpay/retorno"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "http://localhost:3000/pedidos?status=invalido"));
    }

    @Test
    void confirmarTransaccion_conTokenValido_retorna200YDetalle() throws Exception {
        WebpayCommitResponseDTO commitResponse = new WebpayCommitResponseDTO(
                "TSY", 10000.0, "AUTHORIZED", "PEDIDO-1", "SESION-1",
                new WebpayCommitResponseDTO.CardDetail("6670"),
                "0916", "2026-09-16T12:00:00Z", "AUTH123", "VD", 0, null, null, null
        );
        when(webpayService.confirmarTransaccion("token_123")).thenReturn(commitResponse);

        mockMvc.perform(post("/api/v1/pagos/webpay/confirmar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"token_123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AUTHORIZED"))
                .andExpect(jsonPath("$.authorization_code").value("AUTH123"));
    }

    @Test
    void confirmarTransaccion_sinToken_retorna400() throws Exception {
        mockMvc.perform(post("/api/v1/pagos/webpay/confirmar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
