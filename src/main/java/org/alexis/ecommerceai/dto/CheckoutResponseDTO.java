package org.alexis.ecommerceai.dto;

/**
 * Response returned when a payment transaction is initiated (e.g. Webpay Plus).
 */
public record CheckoutResponseDTO(
        String token,
        String url
) {
}
