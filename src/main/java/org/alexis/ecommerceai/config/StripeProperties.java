package org.alexis.ecommerceai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Typed configuration properties for Stripe payments.
 */
@ConfigurationProperties(prefix = "stripe")
public record StripeProperties(
        String apiKey,
        String webhookSecret,
        @DefaultValue("usd") String currency,
        @DefaultValue("http://localhost:3000/pedidos/{CHECKOUT_SESSION_ID}/exito") String successUrl,
        @DefaultValue("http://localhost:3000/pedidos/{CHECKOUT_SESSION_ID}/cancelado") String cancelUrl
) {
    public StripeProperties {
        if (currency == null || currency.isBlank()) {
            currency = "usd";
        }
        if (successUrl == null || successUrl.isBlank()) {
            successUrl = "http://localhost:3000/pedidos/{CHECKOUT_SESSION_ID}/exito";
        }
        if (cancelUrl == null || cancelUrl.isBlank()) {
            cancelUrl = "http://localhost:3000/pedidos/{CHECKOUT_SESSION_ID}/cancelado";
        }
    }
}
