package org.alexis.ecommerceai.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * Ciclo de vida de un pedido. Se persiste como {@code VARCHAR(20)}
 * ({@code @Enumerated(EnumType.STRING)}), por lo que añadir valores no altera
 * el esquema, pero cada transición válida sí es una regla de negocio.
 *
 * <pre>
 * PENDIENTE ──▶ CONFIRMADO ──▶ ENVIADO ──▶ ENTREGADO
 *     │              │             │
 *     └──────────────┴─────────────┴──────▶ CANCELADO
 * </pre>
 *
 * <p>{@code ENTREGADO} y {@code CANCELADO} son estados terminales: no admiten
 * transición posterior.</p>
 */
public enum EstadoPedido {

    PENDIENTE,
    CONFIRMADO,
    ENVIADO,
    ENTREGADO,
    CANCELADO;

    private static final Set<EstadoPedido> TERMINALES = EnumSet.of(ENTREGADO, CANCELADO);

    /** true si el pedido ya no admite más transiciones. */
    public boolean esTerminal() {
        return TERMINALES.contains(this);
    }

    /**
     * Transiciones permitidas desde este estado. Un pedido sólo avanza hacia
     * adelante en la cadena, o se cancela mientras no sea terminal.
     */
    public Set<EstadoPedido> transicionesValidas() {
        return switch (this) {
            case PENDIENTE -> EnumSet.of(CONFIRMADO, CANCELADO);
            case CONFIRMADO -> EnumSet.of(ENVIADO, CANCELADO);
            case ENVIADO -> EnumSet.of(ENTREGADO, CANCELADO);
            case ENTREGADO, CANCELADO -> EnumSet.noneOf(EstadoPedido.class);
        };
    }

    /** true si {@code destino} es alcanzable desde este estado. */
    public boolean puedeTransicionarA(EstadoPedido destino) {
        return destino != null && transicionesValidas().contains(destino);
    }
}
