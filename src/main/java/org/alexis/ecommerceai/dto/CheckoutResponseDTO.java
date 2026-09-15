package org.alexis.ecommerceai.dto;

/**
 * Response returned when a Stripe Checkout session is created.
 */
public record CheckoutResponseDTO(
        String sessionId,
        String checkoutUrl
) {
}
