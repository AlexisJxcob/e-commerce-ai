package org.alexis.ecommerceai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Transbank Webpay Plus integration.
 */
@ConfigurationProperties("webpay")
public record WebpayProperties(
        String commerceCode,
        String apiKey,
        String baseUrl,
        String returnUrl,
        String finalUrl
) {
    public WebpayProperties {
        if (commerceCode == null || commerceCode.isBlank()) {
            commerceCode = "597055555532";
        }
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = "579B532A7440BB0C9079DED94D31EA1615BACEB56610332264630D42D0A36B1C";
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://webpay3gint.transbank.cl";
        }
        if (returnUrl == null || returnUrl.isBlank()) {
            returnUrl = "http://localhost:8080/api/v1/pagos/webpay/retorno";
        }
        if (finalUrl == null || finalUrl.isBlank()) {
            finalUrl = "http://localhost:3000/pedidos";
        }
    }
}
