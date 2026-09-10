package org.alexis.ecommerceai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoriaRequestDTO(
        @NotBlank(message = "El nombre de la categoría es obligatorio")
        @Size(max = 100, message = "El nombre de la categoría no puede exceder 100 caracteres")
        String nombre,

        String descripcion,

        /** Categoría padre; {@code null} para una categoría raíz. */
        Long padreId
) {
}
