package org.alexis.ecommerceai.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "openrouter.api")
public class OpenRouterProperties {

    /**
     * API Key de OpenRouter. Preferible inyectarla con OPENROUTER_API_KEY.
     */
    private String key = "";

    /**
     * Modelo gratuito por defecto.
     */
    private String model = "google/gemini-2.5-flash:free";

    private String baseUrl = "https://openrouter.ai/api/v1";
}
