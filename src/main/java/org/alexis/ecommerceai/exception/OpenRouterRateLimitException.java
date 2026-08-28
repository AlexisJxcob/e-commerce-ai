package org.alexis.ecommerceai.exception;

public class OpenRouterRateLimitException extends OpenRouterException {

    public OpenRouterRateLimitException(String message) {
        super(message, 429);
    }
}
