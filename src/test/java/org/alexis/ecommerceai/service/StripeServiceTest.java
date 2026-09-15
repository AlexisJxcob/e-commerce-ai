package org.alexis.ecommerceai.service;

import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import org.alexis.ecommerceai.config.StripeProperties;
import org.alexis.ecommerceai.dto.CheckoutResponseDTO;
import org.alexis.ecommerceai.exception.ConflictoException;
import org.alexis.ecommerceai.exception.PedidoNotFoundException;
import org.alexis.ecommerceai.model.EstadoPago;
import org.alexis.ecommerceai.model.EstadoPedido;
import org.alexis.ecommerceai.model.ItemPedido;
import org.alexis.ecommerceai.model.Pedido;
import org.alexis.ecommerceai.model.Producto;
import org.alexis.ecommerceai.model.Usuario;
import org.alexis.ecommerceai.repository.PedidoRepository;
import org.alexis.ecommerceai.repository.ProductoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StripeServiceTest {

    @Mock
    private PedidoRepository pedidoRepository;

    @Mock
    private ProductoRepository productoRepository;

    private StripeProperties stripeProperties;
    private TestableStripeService stripeService;

    private static class TestableStripeService extends StripeService {
        private Session mockSession;

        public TestableStripeService(StripeProperties properties,
                                     PedidoRepository pedidoRepository,
                                     ProductoRepository productoRepository) {
            super(properties, pedidoRepository, productoRepository);
        }

        public void setMockSession(Session session) {
            this.mockSession = session;
        }

        @Override
        Session executeCreateSession(SessionCreateParams params) {
            return mockSession;
        }
    }

    @BeforeEach
    void setUp() {
        stripeProperties = new StripeProperties(
                "sk_test_123",
                "whsec_123",
                "usd",
                "http://localhost:3000/pedidos/{CHECKOUT_SESSION_ID}/exito",
                "http://localhost:3000/pedidos/{CHECKOUT_SESSION_ID}/cancelado"
        );
        stripeService = new TestableStripeService(stripeProperties, pedidoRepository, productoRepository);
    }

    @Test
    @DisplayName("toStripeAmount con moneda de 2 decimales (USD, EUR) multiplica por 100")
    void toStripeAmount_conMonedaDosDecimales_multiplicaPorCien() {
        long amountUsd = stripeService.toStripeAmount(new BigDecimal("19.99"), "USD");
        assertThat(amountUsd).isEqualTo(1999L);

        long amountEur = stripeService.toStripeAmount(new BigDecimal("5.50"), "EUR");
        assertThat(amountEur).isEqualTo(550L);
    }

    @Test
    @DisplayName("Ajuste 3: toStripeAmount con moneda zero-decimal (CLP, JPY) NO se multiplica por 100")
    void toStripeAmount_conMonedaZeroDecimal_noMultiplicaPorCien() {
        // CLP (Peso chileno) y JPY (Yen japonés) tienen 0 fraction digits
        long amountClp = stripeService.toStripeAmount(new BigDecimal("15000"), "CLP");
        assertThat(amountClp).isEqualTo(15000L);

        long amountJpy = stripeService.toStripeAmount(new BigDecimal("2500"), "JPY");
        assertThat(amountJpy).isEqualTo(2500L);
    }

    @Test
    @DisplayName("toStripeAmount con monto nulo lanza IllegalArgumentException")
    void toStripeAmount_conMontoNulo_lanzaExcepcion() {
        assertThatThrownBy(() -> stripeService.toStripeAmount(null, "USD"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("crearSesionCheckout con pedido propio genera CheckoutResponseDTO y actualiza stripeSessionId")
    void crearSesionCheckout_conPedidoPropio_exito() {
        Usuario usuario = new Usuario();
        usuario.setId(1L);
        usuario.setUsername("juan");

        Pedido pedido = new Pedido();
        pedido.setId(42L);
        pedido.setUsuario(usuario);
        pedido.setEstado(EstadoPedido.PENDIENTE);
        pedido.setEstadoPago(EstadoPago.PENDIENTE);
        pedido.setTotal(new BigDecimal("100.00"));
        pedido.setFechaCreacion(LocalDateTime.now());

        ItemPedido item = new ItemPedido();
        item.setId(10L);
        item.setPedido(pedido);
        item.setProductoId(5L);
        item.setCantidad(2);
        item.setPrecioUnitario(new BigDecimal("50.00"));
        pedido.setItems(new ArrayList<>(List.of(item)));

        Producto producto = new Producto();
        producto.setId(5L);
        producto.setNombre("Martillo Galponero");

        when(pedidoRepository.findConItemsById(42L)).thenReturn(Optional.of(pedido));
        when(productoRepository.findById(5L)).thenReturn(Optional.of(producto));

        Session session = new Session();
        session.setId("cs_test_session_abc");
        session.setUrl("https://checkout.stripe.com/c/pay/cs_test_session_abc");
        stripeService.setMockSession(session);

        CheckoutResponseDTO response = stripeService.crearSesionCheckout(42L, "juan", false);

        assertThat(response).isNotNull();
        assertThat(response.sessionId()).isEqualTo("cs_test_session_abc");
        assertThat(response.checkoutUrl()).isEqualTo("https://checkout.stripe.com/c/pay/cs_test_session_abc");

        ArgumentCaptor<Pedido> pedidoCaptor = ArgumentCaptor.forClass(Pedido.class);
        verify(pedidoRepository).save(pedidoCaptor.capture());
        assertThat(pedidoCaptor.getValue().getStripeSessionId()).isEqualTo("cs_test_session_abc");
    }

    @Test
    @DisplayName("crearSesionCheckout con pedido ya pagado lanza ConflictoException")
    void crearSesionCheckout_conPedidoYaPagado_lanzaConflictoException() {
        Usuario usuario = new Usuario();
        usuario.setUsername("juan");

        Pedido pedido = new Pedido();
        pedido.setId(42L);
        pedido.setUsuario(usuario);
        pedido.setEstado(EstadoPedido.CONFIRMADO);
        pedido.setEstadoPago(EstadoPago.PAGADO);

        when(pedidoRepository.findConItemsById(42L)).thenReturn(Optional.of(pedido));

        assertThatThrownBy(() -> stripeService.crearSesionCheckout(42L, "juan", false))
                .isInstanceOf(ConflictoException.class)
                .hasMessageContaining("ya se encuentra pagado");
    }

    @Test
    @DisplayName("crearSesionCheckout con pedido cancelado lanza ConflictoException")
    void crearSesionCheckout_conPedidoCancelado_lanzaConflictoException() {
        Usuario usuario = new Usuario();
        usuario.setUsername("juan");

        Pedido pedido = new Pedido();
        pedido.setId(42L);
        pedido.setUsuario(usuario);
        pedido.setEstado(EstadoPedido.CANCELADO);
        pedido.setEstadoPago(EstadoPago.PENDIENTE);

        when(pedidoRepository.findConItemsById(42L)).thenReturn(Optional.of(pedido));

        assertThatThrownBy(() -> stripeService.crearSesionCheckout(42L, "juan", false))
                .isInstanceOf(ConflictoException.class)
                .hasMessageContaining("está cancelado");
    }

    @Test
    @DisplayName("crearSesionCheckout con pedido de otro usuario lanza PedidoNotFoundException")
    void crearSesionCheckout_conPedidoAjeno_lanzaPedidoNotFoundException() {
        Usuario usuario = new Usuario();
        usuario.setUsername("pedro");

        Pedido pedido = new Pedido();
        pedido.setId(42L);
        pedido.setUsuario(usuario);

        when(pedidoRepository.findConItemsById(42L)).thenReturn(Optional.of(pedido));

        assertThatThrownBy(() -> stripeService.crearSesionCheckout(42L, "juan", false))
                .isInstanceOf(PedidoNotFoundException.class);
    }
}
