package org.alexis.ecommerceai.service;

import org.alexis.ecommerceai.config.WebpayProperties;
import org.alexis.ecommerceai.dto.CheckoutResponseDTO;
import org.alexis.ecommerceai.dto.webpay.WebpayCommitResponseDTO;
import org.alexis.ecommerceai.dto.webpay.WebpayCreateRequestDTO;
import org.alexis.ecommerceai.dto.webpay.WebpayCreateResponseDTO;
import org.alexis.ecommerceai.exception.ConflictoException;
import org.alexis.ecommerceai.exception.PedidoNotFoundException;
import org.alexis.ecommerceai.model.EstadoPago;
import org.alexis.ecommerceai.model.EstadoPedido;
import org.alexis.ecommerceai.model.Pedido;
import org.alexis.ecommerceai.model.Usuario;
import org.alexis.ecommerceai.repository.PedidoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebpayServiceTest {

    @Mock
    private RestClient restClient;

    @Mock
    private PedidoRepository pedidoRepository;

    @Mock
    private PedidoService pedidoService;

    private WebpayProperties webpayProperties;
    private WebpayService webpayService;

    @BeforeEach
    void setUp() {
        webpayProperties = new WebpayProperties(
                "597055555532",
                "579B532A7440BB0C9079DED94D31EA1615BACEB56610332264630D42D0A36B1C",
                "https://webpay3gint.transbank.cl",
                "http://localhost:8080/api/v1/pagos/webpay/retorno",
                "http://localhost:3000/pedidos"
        );
        webpayService = spy(new WebpayService(restClient, webpayProperties, pedidoRepository, pedidoService));
    }

    private Pedido crearPedido(Long id, String username, BigDecimal total, EstadoPedido estado, EstadoPago estadoPago) {
        Usuario usuario = new Usuario();
        usuario.setUsername(username);

        Pedido pedido = new Pedido();
        pedido.setId(id);
        pedido.setUsuario(usuario);
        pedido.setTotal(total);
        pedido.setEstado(estado);
        pedido.setEstadoPago(estadoPago);
        return pedido;
    }

    // ---------- toWebpayAmount ----------

    @Test
    void toWebpayAmount_conMontoValido_redondeaCorrectamente() {
        assertThat(webpayService.toWebpayAmount(new BigDecimal("15990.00"))).isEqualTo(15990L);
        assertThat(webpayService.toWebpayAmount(new BigDecimal("15990.49"))).isEqualTo(15990L);
        assertThat(webpayService.toWebpayAmount(new BigDecimal("15990.50"))).isEqualTo(15991L);
    }

    @Test
    void toWebpayAmount_conMontoNulo_lanzaExcepcion() {
        assertThatThrownBy(() -> webpayService.toWebpayAmount(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no puede ser nulo");
    }

    @Test
    void toWebpayAmount_conMontoCeroONegativo_lanzaExcepcion() {
        assertThatThrownBy(() -> webpayService.toWebpayAmount(BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mayor a cero");

        assertThatThrownBy(() -> webpayService.toWebpayAmount(new BigDecimal("-100")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mayor a cero");
    }

    // ---------- crearTransaccion ----------

    @Test
    void crearTransaccion_conPedidoValido_creaTransaccionYRetornaCheckoutResponse() {
        Pedido pedido = crearPedido(10L, "juan", new BigDecimal("12500.00"), EstadoPedido.PENDIENTE, EstadoPago.PENDIENTE);
        when(pedidoRepository.findConItemsById(10L)).thenReturn(Optional.of(pedido));

        WebpayCreateResponseDTO mockResponse = new WebpayCreateResponseDTO(
                "token_123",
                "https://webpay3gint.transbank.cl/webpayserver/initTransaction"
        );
        doReturn(mockResponse).when(webpayService).executeCreate(any(WebpayCreateRequestDTO.class));

        CheckoutResponseDTO result = webpayService.crearTransaccion(10L, "juan", false);

        assertThat(result.token()).isEqualTo("token_123");
        assertThat(result.url()).isEqualTo("https://webpay3gint.transbank.cl/webpayserver/initTransaction");

        assertThat(pedido.getWebpayToken()).isEqualTo("token_123");
        verify(pedidoRepository).save(pedido);
    }

    @Test
    void crearTransaccion_conPedidoDeOtroUsuario_lanzaPedidoNotFoundException() {
        Pedido pedido = crearPedido(10L, "pedro", new BigDecimal("12500.00"), EstadoPedido.PENDIENTE, EstadoPago.PENDIENTE);
        when(pedidoRepository.findConItemsById(10L)).thenReturn(Optional.of(pedido));

        assertThatThrownBy(() -> webpayService.crearTransaccion(10L, "juan", false))
                .isInstanceOf(PedidoNotFoundException.class);
    }

    @Test
    void crearTransaccion_conAdmin_permitePagarPedidoDeOtroUsuario() {
        Pedido pedido = crearPedido(10L, "pedro", new BigDecimal("12500.00"), EstadoPedido.PENDIENTE, EstadoPago.PENDIENTE);
        when(pedidoRepository.findConItemsById(10L)).thenReturn(Optional.of(pedido));

        WebpayCreateResponseDTO mockResponse = new WebpayCreateResponseDTO("token_admin", "https://url.test");
        doReturn(mockResponse).when(webpayService).executeCreate(any(WebpayCreateRequestDTO.class));

        CheckoutResponseDTO result = webpayService.crearTransaccion(10L, "admin", true);

        assertThat(result.token()).isEqualTo("token_admin");
    }

    @Test
    void crearTransaccion_conPedidoYaPagado_lanzaConflictoException() {
        Pedido pedido = crearPedido(10L, "juan", new BigDecimal("12500.00"), EstadoPedido.CONFIRMADO, EstadoPago.PAGADO);
        when(pedidoRepository.findConItemsById(10L)).thenReturn(Optional.of(pedido));

        assertThatThrownBy(() -> webpayService.crearTransaccion(10L, "juan", false))
                .isInstanceOf(ConflictoException.class)
                .hasMessageContaining("ya se encuentra pagado");
    }

    @Test
    void crearTransaccion_conPedidoCancelado_lanzaConflictoException() {
        Pedido pedido = crearPedido(10L, "juan", new BigDecimal("12500.00"), EstadoPedido.CANCELADO, EstadoPago.PENDIENTE);
        when(pedidoRepository.findConItemsById(10L)).thenReturn(Optional.of(pedido));

        assertThatThrownBy(() -> webpayService.crearTransaccion(10L, "juan", false))
                .isInstanceOf(ConflictoException.class)
                .hasMessageContaining("está cancelado");
    }

    // ---------- confirmarTransaccion ----------

    @Test
    void confirmarTransaccion_conPagoAprobado_marcaPedidoComoPagado() {
        Pedido pedido = crearPedido(10L, "juan", new BigDecimal("12500.00"), EstadoPedido.PENDIENTE, EstadoPago.PENDIENTE);
        pedido.setWebpayToken("token_valido");
        when(pedidoRepository.findByWebpayToken("token_valido")).thenReturn(Optional.of(pedido));

        WebpayCommitResponseDTO commitResponse = new WebpayCommitResponseDTO(
                "TSY",
                12500.0,
                "AUTHORIZED",
                "PEDIDO-10",
                "SESION-10",
                new WebpayCommitResponseDTO.CardDetail("6670"),
                "0916",
                "2026-09-16T12:00:00Z",
                "AUTH123",
                "VD",
                0,
                null,
                null,
                null
        );
        doReturn(commitResponse).when(webpayService).executeCommit("token_valido");

        WebpayCommitResponseDTO result = webpayService.confirmarTransaccion("token_valido");

        assertThat(result.isAprobada()).isTrue();
        verify(pedidoService).marcarComoPagado(10L, "AUTH123");
        verify(pedidoService, never()).marcarComoFallido(any());
    }

    @Test
    void confirmarTransaccion_conPagoRechazado_marcaPedidoComoFallido() {
        Pedido pedido = crearPedido(10L, "juan", new BigDecimal("12500.00"), EstadoPedido.PENDIENTE, EstadoPago.PENDIENTE);
        pedido.setWebpayToken("token_rechazado");
        when(pedidoRepository.findByWebpayToken("token_rechazado")).thenReturn(Optional.of(pedido));

        WebpayCommitResponseDTO commitResponse = new WebpayCommitResponseDTO(
                "TSN",
                12500.0,
                "FAILED",
                "PEDIDO-10",
                "SESION-10",
                new WebpayCommitResponseDTO.CardDetail("6670"),
                "0916",
                "2026-09-16T12:00:00Z",
                null,
                "VD",
                -1,
                null,
                null,
                null
        );
        doReturn(commitResponse).when(webpayService).executeCommit("token_rechazado");

        WebpayCommitResponseDTO result = webpayService.confirmarTransaccion("token_rechazado");

        assertThat(result.isAprobada()).isFalse();
        verify(pedidoService).marcarComoFallido(10L);
        verify(pedidoService, never()).marcarComoPagado(any(), any());
    }

    @Test
    void confirmarTransaccion_conTokenInexistente_lanzaPedidoNotFoundException() {
        when(pedidoRepository.findByWebpayToken("token_fantasma")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> webpayService.confirmarTransaccion("token_fantasma"))
                .isInstanceOf(PedidoNotFoundException.class);
    }

    // ---------- registrarCancelacion ----------

    @Test
    void registrarCancelacion_conTokenValido_marcaComoFallido() {
        Pedido pedido = crearPedido(10L, "juan", new BigDecimal("12500.00"), EstadoPedido.PENDIENTE, EstadoPago.PENDIENTE);
        when(pedidoRepository.findByWebpayToken("tbk_token_123")).thenReturn(Optional.of(pedido));

        webpayService.registrarCancelacion("tbk_token_123");

        verify(pedidoService).marcarComoFallido(10L);
    }
}
