package org.alexis.ecommerceai.exception;

import java.time.LocalDateTime;
import java.util.Map;

public record ErrorResponse(
        LocalDateTime timestamp,
        int status,
        String message,
        Map<String, String> fieldErrors
) {
    public ErrorResponse(int status, String message) {
        this(LocalDateTime.now(), status, message, null);
    }

    public ErrorResponse(int status, String message, Map<String, String> fieldErrors) {
        this(LocalDateTime.now(), status, message, fieldErrors);
    }
}