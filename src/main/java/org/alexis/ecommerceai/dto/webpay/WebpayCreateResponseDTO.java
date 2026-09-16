package org.alexis.ecommerceai.dto.webpay;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WebpayCreateResponseDTO(
        String token,
        String url
) {
}
