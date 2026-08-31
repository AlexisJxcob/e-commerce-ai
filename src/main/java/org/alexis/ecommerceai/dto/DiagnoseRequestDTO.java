package org.alexis.ecommerceai.dto;

/**
 * Cuerpo de la petición para POST /api/v1/productos/diagnose.
 * Contrato JSON: { "problema": "..." }
 */
public record DiagnoseRequestDTO(String problema) {
}
