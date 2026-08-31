package org.alexis.ecommerceai.ai;

import org.alexis.ecommerceai.dto.BusquedaInteligenteResponse;
import org.alexis.ecommerceai.dto.ProductoResponseDTO;
import org.alexis.ecommerceai.dto.SugerenciaFerreteriaDTO;
import org.alexis.ecommerceai.service.ProductoService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Stream;

@Service
public class AsistenteIAService {

    private final HuggingFaceChatService huggingFaceChatService;
    private final ProductoService productoService;

    public AsistenteIAService(HuggingFaceChatService huggingFaceChatService, ProductoService productoService) {
        this.huggingFaceChatService = huggingFaceChatService;
        this.productoService = productoService;
    }

    public BusquedaInteligenteResponse buscarRecomendacion(String preferenciaUsuario) {
        SugerenciaFerreteriaDTO sugerencia = huggingFaceChatService.analizarConsulta(preferenciaUsuario);
        List<String> terminos = Stream.of(
                        sugerencia.palabrasClave(),
                        sugerencia.herramientas(),
                        sugerencia.repuestos())
                .flatMap(List::stream)
                .filter(termino -> termino != null && !termino.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        List<ProductoResponseDTO> productos = productoService.buscarPorPalabrasClave(terminos);
        return new BusquedaInteligenteResponse(sugerencia, productos);
    }
}
