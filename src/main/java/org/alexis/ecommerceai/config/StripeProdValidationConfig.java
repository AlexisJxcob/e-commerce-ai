package org.alexis.ecommerceai.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.util.StringUtils;

/**
 * Fail-fast validation for Stripe configuration, active exclusively in the 'prod' profile.
 */
@Configuration
@Profile("prod")
public class StripeProdValidationConfig {

    private final StripeProperties stripeProperties;

    public StripeProdValidationConfig(StripeProperties stripeProperties) {
        this.stripeProperties = stripeProperties;
    }

    @PostConstruct
    public void validateStripeConfig() {
        if (!StringUtils.hasText(stripeProperties.apiKey())) {
            throw new IllegalStateException(
                    "Falta configurar la propiedad 'stripe.api-key' (variable STRIPE_API_KEY) en el perfil de produccion."
            );
        }
        if (!StringUtils.hasText(stripeProperties.webhookSecret())) {
            throw new IllegalStateException(
                    "Falta configurar la propiedad 'stripe.webhook-secret' (variable STRIPE_WEBHOOK_SECRET) en el perfil de produccion."
            );
        }
    }
}
