package org.alexis.ecommerceai.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Pedido tal como lo ve el cliente.
 *
 * <p>Incluye {@code subtotal} y {@code costoDespacho} para que el comprobante
 * pueda desglosar exactamente lo mismo que se cobró, en vez de mostrar
 * únicamente un total sin explicación.</p>
 */
public record PedidoResponseDTO(
        Long id,
        String estado,
        BigDecimal total,
        LocalDateTime fechaCreacion,
        List<ItemPedidoResponseDTO> items,
        String webpayToken,
        String estadoPago,
        BigDecimal subtotal,
        BigDecimal costoDespacho,
        String metodoEntrega
) {

    /** Compatibilidad: pedidos sin desglose de envío (retiro o datos previos). */
    public PedidoResponseDTO(Long id, String estado, BigDecimal total, LocalDateTime fechaCreacion,
                             List<ItemPedidoResponseDTO> items, String webpayToken, String estadoPago) {
        this(id, estado, total, fechaCreacion, items, webpayToken, estadoPago, total, BigDecimal.ZERO, null);
    }

    public PedidoResponseDTO(Long id, String estado, BigDecimal total, LocalDateTime fechaCreacion,
                             List<ItemPedidoResponseDTO> items) {
        this(id, estado, total, fechaCreacion, items, null, "PENDIENTE");
    }
}
