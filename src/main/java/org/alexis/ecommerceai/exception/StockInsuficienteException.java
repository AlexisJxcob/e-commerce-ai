package org.alexis.ecommerceai.exception;

/**
 * Stock insuficiente detectado <b>antes</b> de intentar reservar unidades:
 * la petición es inválida tal como viene → 400 Bad Request.
 *
 * <p>No confundir con {@link StockUpdateConflictException} (409), que se lanza
 * cuando dos peticiones concurrentes compiten por la última unidad y una de
 * ellas pierde el bloqueo optimista. La distinción es deliberada: 400 = "pediste
 * más de lo que hay", 409 = "lo había cuando pediste, alguien se te adelantó".</p>
 */
public class StockInsuficienteException extends RuntimeException {
    public StockInsuficienteException(String message) {
        super(message);
    }
}
