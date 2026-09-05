package org.alexis.ecommerceai.dto;

import java.math.BigDecimal;
import java.util.List;

public record CarritoResponseDTO(
        List<LineaCarritoResponseDTO> items,
        BigDecimal total
) {
}
