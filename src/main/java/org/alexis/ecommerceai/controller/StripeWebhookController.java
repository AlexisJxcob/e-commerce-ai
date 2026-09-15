package org.alexis.ecommerceai.controller;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import org.alexis.ecommerceai.config.StripeProperties;
import org.alexis.ecommerceai.exception.PedidoNotFoundException;
import org.alexis.ecommerceai.exception.TransicionEstadoInvalidaException;
import org.alexis.ecommerceai.service.PedidoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller receiving webhook events from Stripe.
 *
 * NOTA DE ARQUITECTURA (Ajuste 1):
 * El proyecto usa server.servlet.context-path=/api globalmente, por lo que el controlador
 * se declara como @RequestMapping("/v1/pagos") y @PostMapping("/webhook") (sin el prefijo /api).
 * La URL pública completa para configurar en Stripe Dashboard y Stripe CLI es:
 *   http://<host>:<port>/api/v1/pagos/webhook
 */
@RestController
@RequestMapping("/v1/pagos")
public class StripeWebhookController {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookController.class);

    private final StripeProperties stripeProperties;
    private final PedidoService pedidoService;

    public StripeWebhookController(StripeProperties stripeProperties, PedidoService pedidoService) {
        this.stripeProperties = stripeProperties;
        this.pedidoService = pedidoService;
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String sigHeader) {

        if (sigHeader == null || sigHeader.isBlank()) {
            log.warn("Llego webhook de Stripe sin cabecera Stripe-Signature");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing Stripe-Signature header");
        }

        Event event;
        try {
            event = constructEvent(payload, sigHeader, stripeProperties.webhookSecret());
        } catch (SignatureVerificationException e) {
            log.warn("Firma criptografica invalida en webhook de Stripe: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature");
        } catch (Exception e) {
            log.error("Error inesperado al parsear webhook de Stripe: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Error parsing webhook");
        }

        String eventType = event.getType();
        log.info("Evento de Stripe recibido: tipo={}, id={}", eventType, event.getId());

        if ("checkout.session.completed".equals(eventType)) {
            procesarCheckoutSessionCompleted(event);
        } else if ("checkout.session.expired".equals(eventType)) {
            log.info("Sesion de Stripe expirada: id={}", event.getId());
        } else {
            log.debug("Evento no manejado de Stripe: {}", eventType);
        }

        return ResponseEntity.ok("Received");
    }

    private void procesarCheckoutSessionCompleted(Event event) {
        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
        StripeObject stripeObject = deserializer.getObject()
                .orElseGet(() -> {
                    try {
                        return deserializer.deserializeUnsafe();
                    } catch (Exception e) {
                        return null;
                    }
                });

        if (stripeObject instanceof Session session) {
            String clientRef = session.getClientReferenceId();
            if (clientRef == null && session.getMetadata() != null) {
                clientRef = session.getMetadata().get("pedido_id");
            }

            if (clientRef == null || clientRef.isBlank()) {
                log.warn("Sesion completada de Stripe sin client_reference_id ni metadata 'pedido_id': sessionId={}", session.getId());
                return;
            }

            Long pedidoId;
            try {
                pedidoId = Long.parseLong(clientRef);
            } catch (NumberFormatException e) {
                log.error("client_reference_id invalido ({}) en sesion de Stripe: {}", clientRef, session.getId());
                return;
            }

            String paymentIntentId = session.getPaymentIntent();

            try {
                pedidoService.marcarComoPagado(pedidoId, paymentIntentId);
                log.info("Pedido #{} marcado como pagado exitosamente tras checkout.session.completed", pedidoId);
            } catch (TransicionEstadoInvalidaException e) {
                // Ajuste 5: Si el pedido esta en estado no transicionable (ej. CANCELADO), loguear ERROR
                // para intervencion manual/reembolso y responder 200 OK a Stripe para detener reintentos.
                log.error("ERROR CRITICO: No se pudo marcar como pagado el pedido #{}. Estado actual no permite transicionar a CONFIRMADO (paymentIntentId={}): {}",
                        pedidoId, paymentIntentId, e.getMessage());
            } catch (PedidoNotFoundException e) {
                log.error("ERROR: Pedido #{} no encontrado al procesar checkout.session.completed (paymentIntentId={}): {}",
                        pedidoId, paymentIntentId, e.getMessage());
            }
        }
    }

    Event constructEvent(String payload, String sigHeader, String secret) throws SignatureVerificationException {
        return Webhook.constructEvent(payload, sigHeader, secret);
    }
}
