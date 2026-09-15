package org.alexis.ecommerceai.controller;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.checkout.Session;
import org.alexis.ecommerceai.config.StripeProperties;
import org.alexis.ecommerceai.exception.GlobalExceptionHandler;
import org.alexis.ecommerceai.exception.TransicionEstadoInvalidaException;
import org.alexis.ecommerceai.service.PedidoService;
import org.alexis.ecommerceai.testconfig.MockMvcContextPathConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class StripeWebhookControllerTest {

    @Mock
    private PedidoService pedidoService;

    private StripeProperties stripeProperties;
    private TestableWebhookController controller;
    private MockMvc mockMvc;

    private static class TestableWebhookController extends StripeWebhookController {
        private Event eventToReturn;
        private boolean throwSignatureException;

        public TestableWebhookController(StripeProperties properties, PedidoService pedidoService) {
            super(properties, pedidoService);
        }

        public void setEventToReturn(Event event) {
            this.eventToReturn = event;
        }

        public void setThrowSignatureException(boolean throwSignatureException) {
            this.throwSignatureException = throwSignatureException;
        }

        @Override
        Event constructEvent(String payload, String sigHeader, String secret) throws SignatureVerificationException {
            if (throwSignatureException) {
                throw new SignatureVerificationException("Sig header invalid", sigHeader);
            }
            return eventToReturn;
        }
    }

    @BeforeEach
    void setUp() {
        stripeProperties = new StripeProperties(
                "sk_test_dummy",
                "whsec_dummy",
                "usd",
                "http://localhost:3000/pedidos/{CHECKOUT_SESSION_ID}/exito",
                "http://localhost:3000/pedidos/{CHECKOUT_SESSION_ID}/cancelado"
        );
        controller = new TestableWebhookController(stripeProperties, pedidoService);
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .defaultRequest(get("/").contextPath(MockMvcContextPathConfig.CONTEXT_PATH))
                .build();
    }

    @Test
    @DisplayName("Webhook sin cabecera Stripe-Signature devuelve 400")
    void handleWebhook_sinFirma_devuelve400() throws Exception {
        mockMvc.perform(post("/api/v1/pagos/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\": \"evt_123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Missing Stripe-Signature header"));

        verify(pedidoService, never()).marcarComoPagado(any(), any());
    }

    @Test
    @DisplayName("Webhook con firma invalida devuelve 400")
    void handleWebhook_conFirmaInvalida_devuelve400() throws Exception {
        controller.setThrowSignatureException(true);

        mockMvc.perform(post("/api/v1/pagos/webhook")
                        .header("Stripe-Signature", "t=123,v1=invalido")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\": \"evt_123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Invalid signature"));

        verify(pedidoService, never()).marcarComoPagado(any(), any());
    }

    @Test
    @DisplayName("Webhook checkout.session.completed marca pedido como pagado y devuelve 200")
    void handleWebhook_checkoutSessionCompleted_exito() throws Exception {
        Event event = mock(Event.class);
        when(event.getType()).thenReturn("checkout.session.completed");
        when(event.getId()).thenReturn("evt_test_123");

        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        Session session = mock(Session.class);
        when(session.getClientReferenceId()).thenReturn("42");
        when(session.getPaymentIntent()).thenReturn("pi_stripe_abc");
        when(deserializer.getObject()).thenReturn(java.util.Optional.of(session));
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);

        controller.setEventToReturn(event);

        mockMvc.perform(post("/api/v1/pagos/webhook")
                        .header("Stripe-Signature", "t=123,v1=valido")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\": \"checkout.session.completed\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string("Received"));

        verify(pedidoService).marcarComoPagado(42L, "pi_stripe_abc");
    }

    @Test
    @DisplayName("Ajuste 5: Webhook checkout.session.completed con pedido cancelado captura excepcion y responde 200 OK")
    void handleWebhook_checkoutSessionCompleted_conPedidoCancelado_responde200() throws Exception {
        Event event = mock(Event.class);
        when(event.getType()).thenReturn("checkout.session.completed");
        when(event.getId()).thenReturn("evt_test_123");

        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        Session session = mock(Session.class);
        when(session.getClientReferenceId()).thenReturn("99");
        when(session.getPaymentIntent()).thenReturn("pi_stripe_xyz");
        when(deserializer.getObject()).thenReturn(java.util.Optional.of(session));
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);

        controller.setEventToReturn(event);

        doThrow(new TransicionEstadoInvalidaException("Transicion invalida: no se puede pasar de CANCELADO a CONFIRMADO"))
                .when(pedidoService).marcarComoPagado(99L, "pi_stripe_xyz");

        mockMvc.perform(post("/api/v1/pagos/webhook")
                        .header("Stripe-Signature", "t=123,v1=valido")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\": \"checkout.session.completed\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string("Received"));

        verify(pedidoService).marcarComoPagado(99L, "pi_stripe_xyz");
    }

    @Test
    @DisplayName("Webhook con evento no manejado responde 200")
    void handleWebhook_eventoNoManejado_responde200() throws Exception {
        Event event = mock(Event.class);
        when(event.getType()).thenReturn("payment_intent.created");
        when(event.getId()).thenReturn("evt_test_pi");

        controller.setEventToReturn(event);

        mockMvc.perform(post("/api/v1/pagos/webhook")
                        .header("Stripe-Signature", "t=123,v1=valido")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\": \"payment_intent.created\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string("Received"));

        verify(pedidoService, never()).marcarComoPagado(any(), any());
    }
}
