package org.alexis.ecommerceai.dto;

/**
 * Salida pública de usuario. Por contrato NO incluye {@code password}:
 * cualquier campo de credenciales que se agregue aquí debe revisarse contra
 * el test que verifica que ningún endpoint expone el hash.
 */
public record UsuarioResponseDTO(
        Long id,
        String username,
        String rol,
        String email
) {
}
