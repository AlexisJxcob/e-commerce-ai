package org.alexis.ecommerceai.config;

import org.alexis.ecommerceai.exception.HuggingFaceException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;

/**
 * Validación de API keys al iniciar la aplicación.
 * Asegura que las claves necesarias para los servicios de IA estén configuradas.
 */
@Configuration
@EnableConfigurationProperties({
    HuggingFaceProperties.class,
    HuggingFaceChatProperties.class
})
public class ApiKeyValidationConfig {

    private final HuggingFaceProperties huggingFaceProperties;
    private final HuggingFaceChatProperties huggingFaceChatProperties;

    public ApiKeyValidationConfig(
            HuggingFaceProperties huggingFaceProperties,
            HuggingFaceChatProperties huggingFaceChatProperties) {
        this.huggingFaceProperties = huggingFaceProperties;
        this.huggingFaceChatProperties = huggingFaceChatProperties;
    }

    @PostConstruct
    public void validateApiKeys() {
        // Validar API key para embeddings
        if (!StringUtils.hasText(huggingFaceProperties.getKey())) {
            throw new HuggingFaceException(
                "Falta configurar la API key de Hugging Face para embeddings. " +
                "Configura la propiedad 'huggingface.api.key' o la variable de entorno HUGGINGFACE_API_KEY."
            );
        }

        // Validar API key para chat (puede ser la misma)
        if (!StringUtils.hasText(huggingFaceChatProperties.getKey())) {
            // Si no está configurada la key específica para chat, usar la de embeddings
            huggingFaceChatProperties.setKey(huggingFaceProperties.getKey());
        }

        // Validar modelo de embeddings
        if (!StringUtils.hasText(huggingFaceProperties.getModel())) {
            throw new HuggingFaceException(
                "Falta configurar el modelo de embeddings de Hugging Face. " +
                "Configura la propiedad 'huggingface.api.model'."
            );
        }

        // Validar modelo de chat
        if (!StringUtils.hasText(huggingFaceChatProperties.getModel())) {
            throw new HuggingFaceException(
                "Falta configurar el modelo de chat de Hugging Face. " +
                "Configura la propiedad 'huggingface.chat.model'."
            );
        }
    }
}