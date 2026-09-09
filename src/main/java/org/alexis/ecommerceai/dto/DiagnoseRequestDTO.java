package org.alexis.ecommerceai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la petición para POST /api/v1/productos/diagnose.
 * Contrato JSON: { "problema": "..." }
 */
public record DiagnoseRequestDTO(
        @NotBlank(message = "El campo 'problema' es obligatorio y no puede estar vacío")
        @Size(max = 500, message = "El campo 'problema' no puede superar los 500 caracteres")
        String problema
) {
}

