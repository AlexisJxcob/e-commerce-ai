package org.alexis.ecommerceai.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "groq.api")
public class GroqProperties {

    public static final String DEFAULT_MODEL = "qwen/qwen3.8-27b";
    public static final String DEFAULT_BASE_URL = "https://api.groq.com/openai/v1";

    /**
     * API key de Groq. Preferir variable de entorno GROQ_API_KEY.
     */
    private String key = "";

    /**
     * Identificador del modelo de chat en Groq (p. ej. qwen/qwen3.8-27b).
     */
    private String model = DEFAULT_MODEL;

    private String baseUrl = DEFAULT_BASE_URL;

    private String httpReferer = "http://localhost:8080";

    private String appTitle = "Ferreteria IA App";
}
