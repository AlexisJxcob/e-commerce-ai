package org.alexis.ecommerceai.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "huggingface.api")
public class HuggingFaceProperties {

    public static final String DEFAULT_MODEL = "sentence-transformers/all-MiniLM-L6-v2";
    public static final String DEFAULT_BASE_URL = "https://router.huggingface.co/hf-inference/models";

    /**
     * API key de Hugging Face. Preferir variable de entorno HUGGINGFACE_API_KEY.
     */
    private String key = "";

    /**
     * Modelo de embeddings (feature-extraction). Produce vectores de 384
     * dimensiones; debe coincidir con vector(384) en la entidad Producto.
     */
    private String model = DEFAULT_MODEL;

    /**
     * Base URL del router de Hugging Face. El endpoint final de feature-extraction
     * se construye como {baseUrl}/{model}/pipeline/feature-extraction.
     */
    private String baseUrl = DEFAULT_BASE_URL;
}
