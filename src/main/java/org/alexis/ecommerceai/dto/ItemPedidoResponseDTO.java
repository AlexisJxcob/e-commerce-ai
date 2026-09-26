package org.alexis.ecommerceai.dto;

import java.math.BigDecimal;

public record ItemPedidoResponseDTO(
        Long productoId,
        String productoNombre,
        Integer cantidad,
        BigDecimal precioUnitario,
        BigDecimal subtotal
) {

    public ItemPedidoResponseDTO(Long productoId, Integer cantidad,
                                 BigDecimal precioUnitario, BigDecimal subtotal) {
        this(productoId, null, cantidad, precioUnitario, subtotal);
    }
}
