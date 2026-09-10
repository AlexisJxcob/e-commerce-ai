package org.alexis.ecommerceai.dto;

import jakarta.validation.constraints.Email;
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
         * Opcional: si viene informado se valida el formato y se persiste
         * (único a nivel de base de datos, 409 si ya existe).
         */
        @Email(message = "El email no tiene un formato válido")
        @Size(max = 150, message = "El email no puede exceder 150 caracteres")
        String email,

        /**
         * Sugerencia de rol del cliente. Se ignora siempre: el registro
         * público solo crea usuarios CLIENTE.
         */
        String rol
) {
}
