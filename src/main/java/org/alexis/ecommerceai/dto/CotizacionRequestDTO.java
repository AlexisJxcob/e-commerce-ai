package org.alexis.ecommerceai.dto;

import org.alexis.ecommerceai.model.MetodoEntrega;

/**
 * Cuerpo de la cotización de entrega: el cliente pide precio antes de pagar.
 */
public record CotizacionRequestDTO(
        MetodoEntrega metodoEntrega
) {
}
