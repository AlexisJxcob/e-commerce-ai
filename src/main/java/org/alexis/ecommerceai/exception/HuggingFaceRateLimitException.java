package org.alexis.ecommerceai.exception;

public class HuggingFaceRateLimitException extends HuggingFaceException {

    public HuggingFaceRateLimitException(String message) {
        super(message, 429);
    }
}
