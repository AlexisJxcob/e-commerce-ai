package org.alexis.ecommerceai.dto;

import java.math.BigDecimal;

/**
 * Cotización de entrega calculada en el servidor.
 *
 * <p>El checkout muestra exactamente estos montos y el pedido se crea con el
 * mismo cálculo, así que el total en pantalla y el total cobrado coinciden por
 * construcción.</p>
 */
public record CotizacionResponseDTO(
        BigDecimal subtotal,
        BigDecimal costoDespacho,
        BigDecimal total,
        BigDecimal envioGratisDesde
) {
}
