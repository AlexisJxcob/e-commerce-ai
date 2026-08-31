package org.alexis.ecommerceai.ai;

import org.alexis.ecommerceai.config.GroqProperties;
import org.alexis.ecommerceai.dto.SugerenciaFerreteriaDTO;
import org.alexis.ecommerceai.dto.groq.ChatCompletionRequest;
import org.alexis.ecommerceai.dto.groq.ChatCompletionResponse;
import org.alexis.ecommerceai.dto.groq.ChatMessage;
import org.alexis.ecommerceai.exception.GroqException;
import org.alexis.ecommerceai.exception.GroqRateLimitException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class GroqService {

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

    private final RestClient groqRestClient;
    private final GroqProperties properties;
    private final ObjectMapper objectMapper;

    public GroqService(
            @Qualifier("groqRestClient") RestClient groqRestClient,
            GroqProperties properties,
            ObjectMapper objectMapper) {
        this.groqRestClient = groqRestClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public SugerenciaFerreteriaDTO analizarConsulta(String consultaUsuario) {
        if (!StringUtils.hasText(properties.getKey())) {
            throw new GroqException("Falta configurar groq.api.key (o la variable GROQ_API_KEY).");
        }
        if (!StringUtils.hasText(properties.getModel())) {
            throw new GroqException("Falta configurar groq.api.model.");
        }
        if (!StringUtils.hasText(consultaUsuario)) {
            throw new GroqException("La consulta del usuario no puede estar vacía.", 400);
        }

        var request = new ChatCompletionRequest(
                properties.getModel(),
                List.of(
                        new ChatMessage("system", SYSTEM_PROMPT),
                        new ChatMessage("user", consultaUsuario.trim())
                )
        );

        ChatCompletionResponse response = invocarChatCompletions(request);
        return parsearSugerencia(extraerContenido(response));
    }

    private ChatCompletionResponse invocarChatCompletions(ChatCompletionRequest request) {
        try {
            return groqRestClient.post()
                    .uri("/chat/completions")
                    .body(request)
                    .retrieve()
                    .onStatus(status -> status.value() == 429, (req, res) -> {
                        throw new GroqRateLimitException(
                                "Groq alcanzó el límite de peticiones (rate limit). Intente más tarde.");
                    })
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        String body = new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8);
                        throw new GroqException(
                                "Error al consultar Groq (" + res.getStatusCode().value() + "): " + body,
                                res.getStatusCode().value());
                    })
                    .body(ChatCompletionResponse.class);
        } catch (GroqException ex) {
            throw ex;
        } catch (ResourceAccessException ex) {
            throw new GroqException("No se pudo conectar con Groq.", ex);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 429) {
                throw new GroqRateLimitException(
                        "Groq alcanzó el límite de peticiones (rate limit). Intente más tarde.");
            }
            throw new GroqException(
                    "Error al consultar Groq (" + ex.getStatusCode().value() + ").",
                    ex.getStatusCode().value());
        } catch (RestClientException ex) {
            throw new GroqException("Fallo al invocar la API de Groq.", ex);
        }
    }

    private String extraerContenido(ChatCompletionResponse response) {
        if (response == null || response.choices() == null || response.choices().isEmpty()
                || response.choices().getFirst().message() == null
                || !StringUtils.hasText(response.choices().getFirst().message().content())) {
            throw new GroqException("Groq devolvió una respuesta vacía.");
        }
        return response.choices().getFirst().message().content();
    }

    private SugerenciaFerreteriaDTO parsearSugerencia(String content) {
        String json = extraerJson(content);
        try {
            return objectMapper.readValue(json, SugerenciaFerreteriaDTO.class);
        } catch (JacksonException ex) {
            throw new GroqException("No se pudo interpretar el JSON devuelto por el modelo.", ex);
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
