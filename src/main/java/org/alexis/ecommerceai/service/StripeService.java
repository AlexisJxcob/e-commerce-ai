package org.alexis.ecommerceai.service;

import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
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
import org.alexis.ecommerceai.repository.PedidoRepository;
import org.alexis.ecommerceai.repository.ProductoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

/**
 * Service responsible for managing Stripe payment sessions and currency calculations.
 */
@Service
public class StripeService {

    private static final Logger log = LoggerFactory.getLogger(StripeService.class);

    private final StripeProperties stripeProperties;
    private final PedidoRepository pedidoRepository;
    private final ProductoRepository productoRepository;

    public StripeService(StripeProperties stripeProperties,
                         PedidoRepository pedidoRepository,
                         ProductoRepository productoRepository) {
        this.stripeProperties = stripeProperties;
        this.pedidoRepository = pedidoRepository;
        this.productoRepository = productoRepository;
    }

    /**
     * Converts a domain monetary amount (BigDecimal) to the integer amount representation expected by Stripe,
     * dynamically resolving the number of fraction digits for the specified currency.
     * For zero-decimal currencies (e.g. CLP, JPY), the multiplier is 1 (10^0).
     * For standard two-decimal currencies (e.g. USD, EUR), the multiplier is 100 (10^2).
     */
    public long toStripeAmount(BigDecimal amount, String currencyCode) {
        if (amount == null) {
            throw new IllegalArgumentException("El monto no puede ser nulo");
        }
        String normalizedCurrency = currencyCode != null ? currencyCode.trim().toUpperCase() : "USD";
        Currency currency = Currency.getInstance(normalizedCurrency);
        int fractionDigits = currency.getDefaultFractionDigits();
        int exponent = Math.max(fractionDigits, 0);
        BigDecimal factor = BigDecimal.TEN.pow(exponent);
        return amount.multiply(factor).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    /**
     * Creates a Stripe Checkout session for the specified order after validating ownership and status.
     */
    @Transactional
    public CheckoutResponseDTO crearSesionCheckout(Long pedidoId, String username, boolean esAdmin) {
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

        String currency = stripeProperties.currency() != null ? stripeProperties.currency().toLowerCase() : "usd";

        SessionCreateParams.Builder paramsBuilder = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(stripeProperties.successUrl())
                .setCancelUrl(stripeProperties.cancelUrl())
                .setClientReferenceId(pedido.getId().toString())
                .putMetadata("pedido_id", pedido.getId().toString());

        if (pedido.getItems() != null && !pedido.getItems().isEmpty()) {
            for (ItemPedido item : pedido.getItems()) {
                String nombreProducto = productoRepository.findById(item.getProductoId())
                        .map(Producto::getNombre)
                        .orElse("Producto #" + item.getProductoId());

                long unitAmount = toStripeAmount(item.getPrecioUnitario(), currency);

                paramsBuilder.addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setQuantity(Long.valueOf(item.getCantidad()))
                                .setPriceData(
                                        SessionCreateParams.LineItem.PriceData.builder()
                                                .setCurrency(currency)
                                                .setUnitAmount(unitAmount)
                                                .setProductData(
                                                        SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                .setName(nombreProducto)
                                                                .build()
                                                )
                                                .build()
                                )
                                .build()
                );
            }
        } else {
            long totalAmount = toStripeAmount(pedido.getTotal(), currency);
            paramsBuilder.addLineItem(
                    SessionCreateParams.LineItem.builder()
                            .setQuantity(1L)
                            .setPriceData(
                                    SessionCreateParams.LineItem.PriceData.builder()
                                            .setCurrency(currency)
                                            .setUnitAmount(totalAmount)
                                            .setProductData(
                                                    SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                            .setName("Pedido #" + pedido.getId())
                                                            .build()
                                            )
                                            .build()
                            )
                            .build()
            );
        }

        try {
            Session session = executeCreateSession(paramsBuilder.build());
            pedido.setStripeSessionId(session.getId());
            pedidoRepository.save(pedido);
            return new CheckoutResponseDTO(session.getId(), session.getUrl());
        } catch (StripeException e) {
            log.error("Error al crear sesión de checkout en Stripe para pedido {}: {}", pedidoId, e.getMessage(), e);
            throw new RuntimeException("Error al comunicarse con la pasarela de pagos: " + e.getMessage(), e);
        }
    }

    /**
     * Executes the Stripe API call to create a Session. Package-private to facilitate unit testing.
     */
    Session executeCreateSession(SessionCreateParams params) throws StripeException {
        RequestOptions requestOptions = RequestOptions.builder()
                .setApiKey(stripeProperties.apiKey())
                .build();
        return Session.create(params, requestOptions);
    }
}
