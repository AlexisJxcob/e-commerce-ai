package org.alexis.ecommerceai.exception;

public class GroqRateLimitException extends GroqException {

    public GroqRateLimitException(String message) {
        super(message, 429);
    }
}
