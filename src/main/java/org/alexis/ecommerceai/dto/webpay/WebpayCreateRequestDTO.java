package org.alexis.ecommerceai.dto.webpay;

import com.fasterxml.jackson.annotation.JsonProperty;

public record WebpayCreateRequestDTO(
        @JsonProperty("buy_order")
        String buyOrder,
        @JsonProperty("session_id")
        String sessionId,
        @JsonProperty("amount")
        long amount,
        @JsonProperty("return_url")
        String returnUrl
) {
}
