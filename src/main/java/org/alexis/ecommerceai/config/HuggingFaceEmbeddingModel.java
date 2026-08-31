package org.alexis.ecommerceai.config;

import org.alexis.ecommerceai.exception.HuggingFaceException;
import org.alexis.ecommerceai.exception.HuggingFaceRateLimitException;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Implementación de {@link EmbeddingModel} sobre la Inference API de Hugging Face
 * (pipeline feature-extraction). La API de embeddings de HF NO es
 * OpenAI-compatible, por eso se usa un cliente RestClient dedicado apuntando a:
 * <p>{base-url}/{model}/pipeline/feature-extraction</p>
 * <p>Modelo configurado: sentence-transformers/all-MiniLM-L6-v2 → 384 dimensiones
 * (debe coincidir con la columna vector(384) de la tabla productos).</p>
 */
public class HuggingFaceEmbeddingModel implements EmbeddingModel {

    public static final int DIMENSION = 384;

    private final RestClient restClient;
    private final HuggingFaceProperties properties;

    public HuggingFaceEmbeddingModel(RestClient restClient, HuggingFaceProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<String> inputs = request.getInstructions();
        if (inputs == null || inputs.isEmpty()) {
            return new EmbeddingResponse(List.of());
        }
        validarConfiguracion();

        String url = properties.getBaseUrl() + "/" + properties.getModel() + "/pipeline/feature-extraction";
        Map<String, Object> body = Map.of(
                "inputs", inputs,
                "options", Map.of("wait_for_model", true)
        );

        List<List<Double>> resultados;
        try {
            resultados = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
        } catch (RestClientResponseException e) {
            throw traducirErrorHttp(e);
        } catch (ResourceAccessException e) {
            throw new HuggingFaceException("No se pudo conectar con la API de Hugging Face: " + e.getMessage(), e);
        }

        if (resultados == null || resultados.size() != inputs.size()) {
            String cantidad = (resultados == null) ? "null" : String.valueOf(resultados.size());
            throw new HuggingFaceException(
                    "La API de Hugging Face devolvió una respuesta inesperada (" + cantidad + " vectores)");
        }

        List<Embedding> embeddings = new ArrayList<>(resultados.size());
        for (int i = 0; i < resultados.size(); i++) {
            embeddings.add(new Embedding(toFloatArray(resultados.get(i)), i));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(Document document) {
        return embed(document.getFormattedContent());
    }

    @Override
    public int dimensions() {
        return DIMENSION;
    }

    private float[] toFloatArray(List<Double> valores) {
        float[] arr = new float[valores.size()];
        for (int i = 0; i < valores.size(); i++) {
            arr[i] = valores.get(i).floatValue();
        }
        return arr;
    }

    private void validarConfiguracion() {
        if (properties.getKey() == null || properties.getKey().isBlank()) {
            throw new HuggingFaceException("La API key de Hugging Face no está configurada (huggingface.api.key).");
        }
        if (properties.getModel() == null || properties.getModel().isBlank()) {
            throw new HuggingFaceException("El modelo de Hugging Face no está configurado (huggingface.api.model).");
        }
    }

    private HuggingFaceException traducirErrorHttp(RestClientResponseException e) {
        String detalle = extraerMensajeError(e);
        int status = e.getStatusCode().value();
        String mensaje = "Hugging Face respondió con estado " + status + (detalle != null ? ": " + detalle : "");
        if (status == 429) {
            return new HuggingFaceRateLimitException("Rate limit de Hugging Face alcanzado: " + detalle);
        }
        if (status == 401 || status == 403) {
            return new HuggingFaceException("Error de autenticación con Hugging Face (estado " + status + "). " + detalle);
        }
        return new HuggingFaceException(mensaje, status);
    }

    private String extraerMensajeError(RestClientResponseException e) {
        try {
            String body = e.getResponseBodyAsString();
            if (body != null && body.contains("\"error\"")) {
                int ini = body.indexOf("\"error\"");
                int inicio = body.indexOf('"', ini + 7);
                int fin = body.indexOf('"', inicio + 1);
                if (inicio >= 0 && fin > inicio) {
                    return body.substring(inicio + 1, fin);
                }
            }
            return body;
        } catch (Exception ex) {
            return null;
        }
    }
}
