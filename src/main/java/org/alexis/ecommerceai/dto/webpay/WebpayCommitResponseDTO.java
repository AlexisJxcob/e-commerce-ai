package org.alexis.ecommerceai.dto.webpay;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WebpayCommitResponseDTO(
        String vci,
        Double amount,
        String status,
        @JsonProperty("buy_order")
        String buyOrder,
        @JsonProperty("session_id")
        String sessionId,
        @JsonProperty("card_detail")
        CardDetail cardDetail,
        @JsonProperty("accounting_date")
        String accountingDate,
        @JsonProperty("transaction_date")
        String transactionDate,
        @JsonProperty("authorization_code")
        String authorizationCode,
        @JsonProperty("payment_type_code")
        String paymentTypeCode,
        @JsonProperty("response_code")
        Integer responseCode,
        @JsonProperty("installments_amount")
        Double installmentsAmount,
        @JsonProperty("installments_number")
        Integer installmentsNumber,
        Double balance
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CardDetail(
            @JsonProperty("card_number")
            String cardNumber
    ) {
    }

    public boolean isAprobada() {
        return "AUTHORIZED".equalsIgnoreCase(status) && (responseCode == null || responseCode == 0);
    }
}
