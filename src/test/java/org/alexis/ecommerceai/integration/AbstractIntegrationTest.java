package org.alexis.ecommerceai.integration;

import org.alexis.ecommerceai.testconfig.EmbeddingModelTestConfig;
import org.alexis.ecommerceai.testconfig.MockMvcContextPathConfig;
import org.alexis.ecommerceai.testconfig.PropiedadesDatasourceTest;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base común para tests de integración con contexto Spring completo.
 *
 * <p>El motor es PostgreSQL real con pgvector (nunca una base en memoria), para
 * que la columna {@code vector(384)} y el operador {@code <=>} se comporten
 * igual que en producción. El destino lo decide {@link EntornoIntegracion}:
 * una rama Neon de pruebas si {@code NEON_TEST_DATABASE_URL} está definida, o
 * un contenedor {@code pgvector/pgvector:pg16} en caso contrario.</p>
 *
 * <p>La clase se deshabilita cuando no hay ni Neon configurado ni Docker
 * disponible (equivalente al antiguo {@code disabledWithoutDocker = true},
 * pero sin renunciar al modo Neon en máquinas sin Docker).</p>
 */
@EnabledIf("org.alexis.ecommerceai.integration.EntornoIntegracion#disponible")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import({ EmbeddingModelTestConfig.class, MockMvcContextPathConfig.class })
public abstract class AbstractIntegrationTest {

    /** {@code null} en modo Neon: allí no hay contenedor que arrancar. */
    static final PostgreSQLContainer<?> POSTGRES = EntornoIntegracion.contenedor();

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        if (EntornoIntegracion.usarNeon()) {
            registry.add("spring.datasource.url", EntornoIntegracion::urlPooled);
            registry.add("spring.datasource.username", EntornoIntegracion::usuario);
            registry.add("spring.datasource.password", EntornoIntegracion::password);
            // Flyway va por el endpoint directo de Neon, nunca por el pooler.
            registry.add("spring.flyway.url", EntornoIntegracion::urlDirecta);
        } else {
            // Flyway apunta al contenedor (url y credenciales): sin ese override
            // seguiría el DATABASE_DIRECT_URL del entorno, migraría la base real
            // y dejaría el contenedor sin esquema.
            PropiedadesDatasourceTest.registrar(registry, POSTGRES);
        }
        // El esquema lo construye Flyway (V1) sobre una base vacía; la extensión
        // vector la aporta la imagen/la rama Neon. Hibernate queda en validate
        // para detectar desalineación entidad-esquema.
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }
}
