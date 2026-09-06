package org.alexis.ecommerceai.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PedidoResponseDTO(
        Long id,
        String estado,
        BigDecimal total,
        LocalDateTime fechaCreacion,
        List<ItemPedidoResponseDTO> items
) {
}
