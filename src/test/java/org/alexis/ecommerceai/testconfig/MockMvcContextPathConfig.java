package org.alexis.ecommerceai.testconfig;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Fuerza el context-path "{@code /api}" por defecto en todos los requests de
 * MockMvc de los tests de integración.
 *
 * <p>Motivo de esta clase (no es config de rutas ni de properties):
 * {@code server.servlet.context-path=/api} sí está cargado en el
 * {@code Environment} del test (de {@code application.properties}), pero
 * {@code @AutoConfigureMockMvc}/MockMvc bajo {@code WebEnvironment.MOCK} NO lee
 * esa property: crea un {@code MockServletContext} con context-path vacío y
 * requiere que cada request especifique su context-path explícitamente.
 * Unifying aquí evita repetir {@code .contextPath("/api")} request por request y
 * mantiene las rutas de los tests en {@code /api/v1/productos} (el path público
 * real). Reescibir las rutas del test a {@code /v1/productos} ocultaría esta
 * divergencia y no reflejaría el runtime real.
 */
@TestConfiguration
public class MockMvcContextPathConfig {

    /**
     * La URL pública real está bajo {@code /api} (context-path del servidor);
     * los handlers del controller están en {@code /v1/productos}.
     */
    public static final String CONTEXT_PATH = "/api";

    @Bean
    public MockMvcBuilderCustomizer contextPathCustomizer() {
        return builder -> builder.defaultRequest(
                MockMvcRequestBuilders.get("/").contextPath(CONTEXT_PATH));
    }
}
