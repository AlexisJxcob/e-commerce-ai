package org.alexis.ecommerceai.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "openrouter.api")
public class OpenRouterProperties {

    public static final String DEFAULT_MODEL = "openrouter/free";
    public static final String DEFAULT_BASE_URL = "https://openrouter.ai/api/v1";

    /**
     * API key de OpenRouter. Preferir variable de entorno OPENROUTER_API_KEY.
     */
    private String key = "";

    /**
     * Identificador del modelo en OpenRouter (p. ej. meta-llama/llama-3.3-70b-instruct:free
     * o openrouter/free).
     */
    private String model = DEFAULT_MODEL;

    private String baseUrl = DEFAULT_BASE_URL;

    private String httpReferer = "http://localhost:8080";

    private String appTitle = "Ferreteria IA App";
}
