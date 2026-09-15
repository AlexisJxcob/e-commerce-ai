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
import org.alexis.ecommerceai.repository.PedidoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Service responsible for managing Transbank Webpay Plus transactions.
 */
@Service
public class WebpayService {

    private static final Logger log = LoggerFactory.getLogger(WebpayService.class);
    private static final String TRANSACTIONS_PATH = "/rswebpaytransaction/api/webpay/v1.2/transactions";

    private final RestClient webpayRestClient;
    private final WebpayProperties webpayProperties;
    private final PedidoRepository pedidoRepository;
    private final PedidoService pedidoService;

    public WebpayService(@Qualifier("webpayRestClient") RestClient webpayRestClient,
                         WebpayProperties webpayProperties,
                         PedidoRepository pedidoRepository,
                         PedidoService pedidoService) {
        this.webpayRestClient = webpayRestClient;
        this.webpayProperties = webpayProperties;
        this.pedidoRepository = pedidoRepository;
        this.pedidoService = pedidoService;
    }

    /**
     * Converts a domain monetary amount to the CLP integer representation expected by Webpay Plus.
     */
    public long toWebpayAmount(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("El monto no puede ser nulo");
        }
        long valor = amount.setScale(0, RoundingMode.HALF_UP).longValueExact();
        if (valor <= 0) {
            throw new IllegalArgumentException("El monto debe ser mayor a cero");
        }
        return valor;
    }

    /**
     * Creates a Webpay Plus transaction for the specified order after validating ownership and status.
     */
    @Transactional
    public CheckoutResponseDTO crearTransaccion(Long pedidoId, String username, boolean esAdmin) {
        Pedido pedido = pedidoRepository.findConItemsById(pedidoId)
                .orElseThrow(() -> new PedidoNotFoundException("Pedido no encontrado con id: " + pedidoId));

        if (!esAdmin && !pedido.getUsuario().getUsername().equals(username)) {
            throw new PedidoNotFoundException("Pedido no encontrado con id: " + pedidoId);
        }

        if (pedido.getEstadoPago() == EstadoPago.PAGADO) {
            throw new ConflictoException("El pedido #" + pedidoId + " ya se encuentra pagado");
        }

        if (pedido.getEstado() == EstadoPedido.CANCELADO) {
            throw new ConflictoException("El pedido #" + pedidoId + " está cancelado y no puede ser pagado");
        }

        long montoClp = toWebpayAmount(pedido.getTotal());
        String buyOrder = "PEDIDO-" + pedido.getId();
        String sessionId = "SESION-" + pedido.getId() + "-" + System.currentTimeMillis();

        WebpayCreateRequestDTO request = new WebpayCreateRequestDTO(
                buyOrder,
                sessionId,
                montoClp,
                webpayProperties.returnUrl()
        );

        try {
            WebpayCreateResponseDTO response = executeCreate(request);
            if (response == null || response.token() == null || response.url() == null) {
                throw new IllegalStateException("Respuesta incompleta desde Transbank Webpay Plus");
            }

            pedido.setWebpayToken(response.token());
            pedido.setEstadoPago(EstadoPago.PENDIENTE);
            pedidoRepository.save(pedido);

            log.info("Transacción Webpay creada para pedido #{}: token={}", pedidoId, response.token());
            return new CheckoutResponseDTO(response.token(), response.url());
        } catch (RestClientException e) {
            log.error("Error al comunicarse con Transbank Webpay para pedido #{}: {}", pedidoId, e.getMessage(), e);
            throw new RuntimeException("Error al comunicarse con la pasarela de pagos Webpay: " + e.getMessage(), e);
        }
    }

    /**
     * Confirms (commits) an authorized transaction with Transbank Webpay Plus.
     */
    @Transactional
    public WebpayCommitResponseDTO confirmarTransaccion(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("El token de Webpay no puede ser nulo o vacío");
        }

        Pedido pedido = pedidoRepository.findByWebpayToken(token)
                .orElseThrow(() -> new PedidoNotFoundException("No existe un pedido asociado al token de Webpay: " + token));

        try {
            WebpayCommitResponseDTO commitResponse = executeCommit(token);
            if (commitResponse == null) {
                throw new IllegalStateException("Respuesta nula al confirmar transacción en Webpay");
            }

            log.info("Resultado confirmación Webpay para pedido #{}: status={}, responseCode={}",
                    pedido.getId(), commitResponse.status(), commitResponse.responseCode());

            if (commitResponse.isAprobada()) {
                pedidoService.marcarComoPagado(pedido.getId(), commitResponse.authorizationCode());
                log.info("Pedido #{} marcado como pagado exitosamente tras confirmación Webpay", pedido.getId());
            } else {
                pedidoService.marcarComoFallido(pedido.getId());
                log.warn("Transacción Webpay rechazada o fallida para pedido #{}: status={}", pedido.getId(), commitResponse.status());
            }

            return commitResponse;
        } catch (RestClientException e) {
            log.error("Error al confirmar transacción con Transbank Webpay (token={}): {}", token, e.getMessage(), e);
            pedidoService.marcarComoFallido(pedido.getId());
            throw new RuntimeException("Error al confirmar transacción en Webpay: " + e.getMessage(), e);
        }
    }

    /**
     * Handles aborted/cancelled payment when Transbank returns TBK_TOKEN.
     */
    @Transactional
    public void registrarCancelacion(String token) {
        if (token != null && !token.isBlank()) {
            pedidoRepository.findByWebpayToken(token).ifPresent(pedido -> {
                pedidoService.marcarComoFallido(pedido.getId());
                log.info("Pago de pedido #{} registrado como cancelado/fallido", pedido.getId());
            });
        }
    }

    WebpayCreateResponseDTO executeCreate(WebpayCreateRequestDTO request) {
        return webpayRestClient.post()
                .uri(TRANSACTIONS_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(WebpayCreateResponseDTO.class);
    }

    WebpayCommitResponseDTO executeCommit(String token) {
        return webpayRestClient.put()
                .uri(TRANSACTIONS_PATH + "/{token}", token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of())
                .retrieve()
                .body(WebpayCommitResponseDTO.class);
    }
}
