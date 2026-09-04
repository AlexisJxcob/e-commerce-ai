package org.alexis.ecommerceai.dto;

public record LoginResponse(
        String token,
        String username,
        long expiresIn
) {
}
