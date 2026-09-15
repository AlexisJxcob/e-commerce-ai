package org.alexis.ecommerceai.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.util.StringUtils;

/**
 * Fail-fast validation for Transbank Webpay Plus configuration in 'prod' profile.
 */
@Configuration
@Profile("prod")
public class WebpayProdValidationConfig {

    private static final String DEFAULT_DEV_COMMERCE_CODE = "597055555532";

    private final WebpayProperties webpayProperties;

    public WebpayProdValidationConfig(WebpayProperties webpayProperties) {
        this.webpayProperties = webpayProperties;
    }

    @PostConstruct
    public void validateWebpayConfig() {
        if (!StringUtils.hasText(webpayProperties.commerceCode()) ||
                DEFAULT_DEV_COMMERCE_CODE.equals(webpayProperties.commerceCode())) {
            throw new IllegalStateException(
                    "Falta configurar el codigo de comercio de produccion de Webpay (variable WEBPAY_COMMERCE_CODE)."
            );
        }
        if (!StringUtils.hasText(webpayProperties.apiKey())) {
            throw new IllegalStateException(
                    "Falta configurar la API key de produccion de Webpay (variable WEBPAY_API_KEY)."
            );
        }
    }
}
