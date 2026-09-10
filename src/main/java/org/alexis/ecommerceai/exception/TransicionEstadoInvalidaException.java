package org.alexis.ecommerceai.exception;

/**
 * Transición de estado de pedido no permitida por la máquina de estados
 * ({@code EstadoPedido.transicionesValidas()}) → 409 Conflict.
 */
public class TransicionEstadoInvalidaException extends ConflictoException {
    public TransicionEstadoInvalidaException(String message) {
        super(message);
    }
}
