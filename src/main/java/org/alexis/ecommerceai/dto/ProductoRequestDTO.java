package org.alexis.ecommerceai.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record ProductoRequestDTO(
        @NotBlank(message = "El SKU es obligatorio")
        @Size(max = 50, message = "El SKU no puede exceder 50 caracteres")
        String sku,

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100, message = "El nombre no puede exceder 100 caracteres")
        String nombre,

        @NotNull(message = "El precio es obligatorio")
        @DecimalMin(value = "0.0", inclusive = false, message = "El precio debe ser mayor que 0")
        BigDecimal precio,

        @NotNull(message = "El stock inicial es obligatorio")
        @Min(value = 0, message = "El stock no puede ser negativo")
        Integer stock
) {}