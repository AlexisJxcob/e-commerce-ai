package org.alexis.ecommerceai.dto;

import java.math.BigDecimal;

public record ProductoResponseDTO(
        Long id,
        String sku,
        String nombre,
        String descripcionTecnica,
        String descripcionColoquial,
        BigDecimal precio,
        Integer stock
) {
}
