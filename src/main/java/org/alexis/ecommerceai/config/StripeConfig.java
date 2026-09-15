package org.alexis.ecommerceai.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class enabling Stripe properties.
 */
@Configuration
@EnableConfigurationProperties(StripeProperties.class)
public class StripeConfig {
}
