package org.alexis.ecommerceai.exception;

import lombok.Getter;

@Getter
public class OpenRouterException extends RuntimeException {

    private final int status;

    public OpenRouterException(String message) {
        this(message, 502);
    }

    public OpenRouterException(String message, int status) {
        super(message);
        this.status = status;
    }

    public OpenRouterException(String message, Throwable cause) {
        super(message, cause);
        this.status = 502;
    }
}
