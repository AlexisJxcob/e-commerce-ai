package org.alexis.ecommerceai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequestDTO(
        @NotBlank(message = "El nombre de usuario es obligatorio")
        @Size(max = 50, message = "El nombre de usuario no puede exceder 50 caracteres")
        String username,

        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
        String password,

        /**
         * Sugerencia de rol del cliente. Se ignora siempre: el registro
         * público solo crea usuarios CLIENTE.
         */
        String rol
) {
}
