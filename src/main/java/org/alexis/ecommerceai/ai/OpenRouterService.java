package org.alexis.ecommerceai.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.alexis.ecommerceai.config.OpenRouterProperties;
import org.alexis.ecommerceai.dto.SugerenciaFerreteriaDTO;
import org.alexis.ecommerceai.dto.openrouter.ChatCompletionRequest;
import org.alexis.ecommerceai.dto.openrouter.ChatCompletionResponse;
import org.alexis.ecommerceai.dto.openrouter.ChatMessage;
import org.alexis.ecommerceai.exception.OpenRouterException;
import org.alexis.ecommerceai.exception.OpenRouterRateLimitException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class OpenRouterService {

    static final String SYSTEM_PROMPT = """
            Eres el asistente técnico de una ferretería. El cliente describe un problema en lenguaje coloquial \
            (ejemplo: "tengo una fuga en una tubería de PVC").
            
            Debes responder ÚNICAMENTE con un JSON válido, sin markdown, sin explicaciones y sin texto extra. \
            El esquema obligatorio es:
            {
              "palabrasClave": ["términos de búsqueda para el catálogo"],
              "herramientas": ["herramientas recomendadas"],
              "repuestos": ["repuestos, consumibles o materiales sugeridos"]
            }
            
            Reglas:
            1. Traduce el problema a términos de ferretería (nombres técnicos y coloquiales).
            2. Incluye 3 a 8 palabrasClave útiles para buscar productos en inventario.
            3. Separa herramientas (llaves, cortatubos, destornilladores, etc.) de repuestos (codos, cinta teflón, pegamento, etc.).
            4. No inventes marcas ni códigos de producto. No des instrucciones largas: solo listas cortas.
            5. Si el mensaje no es de ferretería, igual responde el mismo JSON con listas vacías.
            """;

    private final RestClient openRouterRestClient;
    private final OpenRouterProperties properties;
    private final ObjectMapper objectMapper;

    public OpenRouterService(
            @Qualifier("openRouterRestClient") RestClient openRouterRestClient,
            OpenRouterProperties properties,
            ObjectMapper objectMapper) {
        this.openRouterRestClient = openRouterRestClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public SugerenciaFerreteriaDTO analizarConsulta(String consultaUsuario) {
        if (properties.getKey() == null || properties.getKey().isBlank()) {
            throw new OpenRouterException("Falta configurar openrouter.api.key (o la variable OPENROUTER_API_KEY).");
        }

        var request = new ChatCompletionRequest(
                properties.getModel(),
                List.of(
                        new ChatMessage("system", SYSTEM_PROMPT),
                        new ChatMessage("user", consultaUsuario)
                )
        );

        ChatCompletionResponse response;
        try {
            response = openRouterRestClient.post()
                    .uri("/chat/completions")
                    .body(request)
                    .retrieve()
                    .onStatus(status -> status.value() == 429, (req, res) -> {
                        throw new OpenRouterRateLimitException(
                                "OpenRouter alcanzó el límite de peticiones (rate limit). Intente más tarde.");
                    })
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        String body = new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8);
                        throw new OpenRouterException(
                                "Error al consultar OpenRouter (" + res.getStatusCode().value() + "): " + body,
                                res.getStatusCode().value());
                    })
                    .body(ChatCompletionResponse.class);
        } catch (OpenRouterException ex) {
            throw ex;
        } catch (ResourceAccessException ex) {
            throw new OpenRouterException("No se pudo conectar con OpenRouter.", ex);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 429) {
                throw new OpenRouterRateLimitException(
                        "OpenRouter alcanzó el límite de peticiones (rate limit). Intente más tarde.");
            }
            throw new OpenRouterException(
                    "Error al consultar OpenRouter (" + ex.getStatusCode().value() + ").",
                    ex.getStatusCode().value());
        } catch (RestClientException ex) {
            throw new OpenRouterException("Fallo al invocar la API de OpenRouter.", ex);
        }

        String content = extraerContenido(response);
        return parsearSugerencia(content);
    }

    private String extraerContenido(ChatCompletionResponse response) {
        if (response == null || response.choices() == null || response.choices().isEmpty()
                || response.choices().getFirst().message() == null
                || response.choices().getFirst().message().content() == null
                || response.choices().getFirst().message().content().isBlank()) {
            throw new OpenRouterException("OpenRouter devolvió una respuesta vacía.");
        }
        return response.choices().getFirst().message().content();
    }

    private SugerenciaFerreteriaDTO parsearSugerencia(String content) {
        String json = extraerJson(content);
        try {
            return objectMapper.readValue(json, SugerenciaFerreteriaDTO.class);
        } catch (JsonProcessingException ex) {
            throw new OpenRouterException("No se pudo interpretar el JSON devuelto por el modelo.", ex);
        }
    }

    static String extraerJson(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                trimmed = trimmed.substring(firstNewline + 1, lastFence).trim();
            }
        }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }
}
