package org.alexis.ecommerceai.ai;

import org.alexis.ecommerceai.dto.BusquedaInteligenteResponse;
import org.alexis.ecommerceai.dto.ProductoResponseDTO;
import org.alexis.ecommerceai.dto.SugerenciaFerreteriaDTO;
import org.alexis.ecommerceai.service.ProductoService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AsistenteIAService {

    private final ProductoService productoService;

    public AsistenteIAService(ProductoService productoService) {
        this.productoService = productoService;
    }

    public BusquedaInteligenteResponse buscarRecomendacion(String preferenciaUsuario) {
        SugerenciaFerreteriaDTO sugerenciaMock = new SugerenciaFerreteriaDTO(
                List.of(preferenciaUsuario),
                List.of(),
                List.of()
        );

        // Llama a la búsqueda vectorial pgvector en ProductoService (top 5 resultados)
        List<ProductoResponseDTO> productos = productoService.buscarPorSimilitud(preferenciaUsuario, 5);

        return new BusquedaInteligenteResponse(sugerenciaMock, productos);
    }
}