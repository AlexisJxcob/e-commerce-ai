package org.alexis.ecommerceai.exception;

/**
 * No hay nada que pedir: el usuario no tiene carrito o su carrito está vacío.
 * Es una petición inválida, no un conflicto → 400 Bad Request.
 */
public class CarritoVacioException extends RuntimeException {
    public CarritoVacioException(String message) {
        super(message);
    }
}
