package org.alexis.ecommerceai.ai;

import org.alexis.ecommerceai.dto.BusquedaInteligenteResponse;
import org.alexis.ecommerceai.dto.ProductoResponseDTO;
import org.alexis.ecommerceai.dto.SugerenciaFerreteriaDTO;
import org.alexis.ecommerceai.service.ProductoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AsistenteIAServiceTest {

    @Mock
    private HuggingFaceChatService huggingFaceChatService;

    @Mock
    private ProductoService productoService;

    private AsistenteIAService asistenteIAService;

    @BeforeEach
    void setUp() {
        asistenteIAService = new AsistenteIAService(huggingFaceChatService, productoService);
    }

    private static ProductoResponseDTO producto(Long id, String sku) {
        return new ProductoResponseDTO(id, sku, "Cinta", "tec", "col",
                new BigDecimal("10.00"), 5);
    }

    @Test
    void buscarRecomendacion_combinaTerminosYBuscaProductos() {
        SugerenciaFerreteriaDTO sugerencia = new SugerenciaFerreteriaDTO(
                List.of("cinta", "teflon"),
                List.of("llave", "cinta"),
                List.of("cinta teflon"));
        ProductoResponseDTO producto = producto(1L, "SKU-1");
        when(huggingFaceChatService.analizarConsulta("fuga en tuberia")).thenReturn(sugerencia);
        when(productoService.buscarPorPalabrasClave(
                List.of("cinta", "teflon", "llave", "cinta teflon")))
                .thenReturn(List.of(producto));

        BusquedaInteligenteResponse response = asistenteIAService.buscarRecomendacion("fuga en tuberia");

        assertThat(response.sugerencia()).isSameAs(sugerencia);
        assertThat(response.productos()).containsExactly(producto);
        verify(huggingFaceChatService).analizarConsulta("fuga en tuberia");
    }

    @Test
    void buscarRecomendacion_filtraTerminosNulosYEnBlanco() {
        SugerenciaFerreteriaDTO sugerencia = new SugerenciaFerreteriaDTO(
                List.of("cinta", " ", "teflon"),
                Arrays.asList("", null, "llave"),
                List.of());
        when(huggingFaceChatService.analizarConsulta("consulta")).thenReturn(sugerencia);
        when(productoService.buscarPorPalabrasClave(List.of("cinta", "teflon", "llave")))
                .thenReturn(List.of());

        BusquedaInteligenteResponse response = asistenteIAService.buscarRecomendacion("consulta");

        assertThat(response.productos()).isEmpty();
        verify(productoService).buscarPorPalabrasClave(List.of("cinta", "teflon", "llave"));
    }

    @Test
    void buscarRecomendacion_aceptaListasNulas() {
        SugerenciaFerreteriaDTO sugerencia = new SugerenciaFerreteriaDTO(null, null, null);
        when(huggingFaceChatService.analizarConsulta("consulta")).thenReturn(sugerencia);
        when(productoService.buscarPorPalabrasClave(List.of())).thenReturn(List.of());

        BusquedaInteligenteResponse response = asistenteIAService.buscarRecomendacion("consulta");

        assertThat(response.sugerencia()).isSameAs(sugerencia);
        assertThat(response.productos()).isEmpty();
    }
}
