package org.alexis.ecommerceai.dto;

import java.math.BigDecimal;

public record LineaCarritoResponseDTO(
        Long id,
        Long productoId,
        String nombre,
        BigDecimal precioUnitario,
        Integer cantidad,
        BigDecimal subtotal
) {
}
