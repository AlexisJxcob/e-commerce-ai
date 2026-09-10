package org.alexis.ecommerceai.dto;

public record CategoriaResponseDTO(
        Long id,
        String nombre,
        String descripcion,
        Long padreId
) {
}
