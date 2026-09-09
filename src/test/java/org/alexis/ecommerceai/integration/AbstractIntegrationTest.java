package org.alexis.ecommerceai.integration;

import org.alexis.ecommerceai.testconfig.EmbeddingModelTestConfig;
import org.alexis.ecommerceai.testconfig.MockMvcContextPathConfig;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base común para tests de integración con contexto Spring completo.
 * Usa Testcontainers con la imagen oficial de PostgreSQL + pgvector
 * (en lugar de una base en memoria), para que la columna vector(384)
 * y la búsqueda con el operador <=> funcionen igual que en producción.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import({ EmbeddingModelTestConfig.class, MockMvcContextPathConfig.class })
public abstract class AbstractIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
            .withDatabaseName("ecommerce_test")
            .withUsername("test")
            .withPassword("test")
            .withInitScript("pgvector-init.sql");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // El esquema lo construye Flyway (V1) sobre el contenedor vacío; la
        // extensión vector la aporta la imagen y el init-script. Hibernate
        // queda en validate para detectar desalineación entidad-esquema.
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }
}
