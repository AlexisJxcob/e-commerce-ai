package org.alexis.ecommerceai.ai;

import org.alexis.ecommerceai.dto.BusquedaInteligenteResponse;
import org.alexis.ecommerceai.dto.ProductoResponseDTO;
import org.alexis.ecommerceai.dto.SugerenciaFerreteriaDTO;
import org.alexis.ecommerceai.service.ProductoService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AsistenteIAService {

    private final OpenRouterService openRouterService;
    private final ProductoService productoService;

    public AsistenteIAService(OpenRouterService openRouterService, ProductoService productoService) {
        this.openRouterService = openRouterService;
        this.productoService = productoService;
    }

    public BusquedaInteligenteResponse buscarRecomendacion(String preferenciaUsuario) {
        SugerenciaFerreteriaDTO sugerencia = openRouterService.analizarConsulta(preferenciaUsuario);

        List<String> terminos = new ArrayList<>();
        terminos.addAll(sugerencia.palabrasClave());
        terminos.addAll(sugerencia.herramientas());
        terminos.addAll(sugerencia.repuestos());

        List<ProductoResponseDTO> productos = productoService.buscarPorPalabrasClave(terminos);
        return new BusquedaInteligenteResponse(sugerencia, productos);
    }
}
