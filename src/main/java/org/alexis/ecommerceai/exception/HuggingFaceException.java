package org.alexis.ecommerceai.exception;

import lombok.Getter;

@Getter
public class HuggingFaceException extends RuntimeException {

    private final int status;

    public HuggingFaceException(String message) {
        this(message, 502);
    }

    public HuggingFaceException(String message, int status) {
        super(message);
        this.status = status;
    }

    public HuggingFaceException(String message, Throwable cause) {
        super(message, cause);
        this.status = 502;
    }
}
