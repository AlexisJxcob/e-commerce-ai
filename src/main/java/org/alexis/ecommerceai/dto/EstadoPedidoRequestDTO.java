package org.alexis.ecommerceai.dto;

import jakarta.validation.constraints.NotNull;
import org.alexis.ecommerceai.model.EstadoPedido;

/**
 * Transición de estado solicitada por un administrador. La validez de la
 * transición no se decide aquí sino en {@link EstadoPedido#puedeTransicionarA}
 * (regla de dominio), y el rol se toma del JWT, nunca del cuerpo.
 */
public record EstadoPedidoRequestDTO(
        @NotNull(message = "El estado es obligatorio")
        EstadoPedido estado
) {
}
