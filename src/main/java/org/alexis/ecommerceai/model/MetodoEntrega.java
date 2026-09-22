package org.alexis.ecommerceai.model;

/**
 * Forma de entrega elegida por el comprador al cerrar el pedido.
 *
 * <p>{@link #RETIRO} siempre es sin costo. {@link #DESPACHO} queda sujeto a la
 * regla de envío vigente, cuyo único dueño es {@code EnvioService}.</p>
 */
public enum MetodoEntrega {
    RETIRO,
    DESPACHO
}
