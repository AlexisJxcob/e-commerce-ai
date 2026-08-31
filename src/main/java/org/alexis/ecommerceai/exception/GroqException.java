package org.alexis.ecommerceai.exception;

import lombok.Getter;

@Getter
public class GroqException extends RuntimeException {

    private final int status;

    public GroqException(String message) {
        this(message, 502);
    }

    public GroqException(String message, int status) {
        super(message);
        this.status = status;
    }

    public GroqException(String message, Throwable cause) {
        super(message, cause);
        this.status = 502;
    }
}
