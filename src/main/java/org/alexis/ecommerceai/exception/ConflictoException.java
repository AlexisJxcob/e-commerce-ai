package org.alexis.ecommerceai.exception;

/**
 * Base para errores de dominio de conflicto (HTTP 409).
 * Las excepciones hijas (duplicados, categoría en uso, producto con pedidos,
 * stock insuficiente) heredan este mapeo.
 */
public class ConflictoException extends RuntimeException {
    public ConflictoException(String message) {
        super(message);
    }
}
