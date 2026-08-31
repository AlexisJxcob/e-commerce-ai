package org.alexis.ecommerceai.dto;

/**
 * Resultado de una operación de reindexación masiva de embeddings.
 */
public record ReindexacionResponse(int procesados, int pendientes) {
}
