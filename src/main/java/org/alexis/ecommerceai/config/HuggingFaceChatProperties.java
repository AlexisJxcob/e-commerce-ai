package org.alexis.ecommerceai.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "huggingface.chat")
public class HuggingFaceChatProperties {

    public static final String DEFAULT_MODEL = "Meta-Llama/Llama-3.2-3B-Instruct";
    public static final String DEFAULT_BASE_URL = "https://router.huggingface.co/v1";

    /**
     * API key de Hugging Face para el chat. Preferir variable de entorno
     * HUGGINGFACE_API_KEY (la misma que usan los embeddings).
     */
    private String key = "";

    /**
     * Identificador del modelo de chat en la Inference API de Hugging Face
     * (p. ej. Meta-Llama/Llama-3.2-3B-Instruct o Qwen/Qwen2.5-Coder-7B-Instruct).
     */
    private String model = DEFAULT_MODEL;

    /**
     * Base URL del router de Hugging Face. El endpoint final de chat se
     * construye como {baseUrl}/chat/completions.
     */
    private String baseUrl = DEFAULT_BASE_URL;
}
