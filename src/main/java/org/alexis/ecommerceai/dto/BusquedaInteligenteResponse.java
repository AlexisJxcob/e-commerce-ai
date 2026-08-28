package org.alexis.ecommerceai.dto;

import java.util.List;

public record BusquedaInteligenteResponse(
        SugerenciaFerreteriaDTO sugerencia,
        List<ProductoResponseDTO> productos
) {
}
