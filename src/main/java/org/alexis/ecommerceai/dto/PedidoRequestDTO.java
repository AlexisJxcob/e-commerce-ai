package org.alexis.ecommerceai.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record PedidoRequestDTO(
        @NotEmpty(message = "El pedido debe tener al menos una línea")
        List<@Valid LineaPedidoDTO> items
) {
}
