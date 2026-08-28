package org.alexis.ecommerceai.exception;

public class StockUpdateConflictException extends RuntimeException {
    public StockUpdateConflictException(String message) {
        super(message);
    }
}
