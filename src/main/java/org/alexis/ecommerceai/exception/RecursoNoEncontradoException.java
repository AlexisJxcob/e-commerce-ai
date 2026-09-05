package org.alexis.ecommerceai.exception;

/**
 * Base para errores de dominio "no encontrado" (HTTP 404).
 * Las excepciones hijas (categoría, pedido, línea de carrito) heredan este mapeo.
 */
public class RecursoNoEncontradoException extends RuntimeException {
    public RecursoNoEncontradoException(String message) {
        super(message);
    }
}
