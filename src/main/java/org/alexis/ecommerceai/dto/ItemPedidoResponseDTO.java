package org.alexis.ecommerceai.dto;

import java.math.BigDecimal;

public record ItemPedidoResponseDTO(
        Long productoId,
        Integer cantidad,
        BigDecimal precioUnitario,
        BigDecimal subtotal
) {
}
