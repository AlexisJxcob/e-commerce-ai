package org.alexis.ecommerceai.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base para los tests de repositorio ({@code @DataJpaTest}) contra PostgreSQL
 * real con pgvector.
 *
 * <p>El slice de JPA no sustituye la base por una embebida ({@code replace =
 * NONE}): cada subclase apunta a su contenedor Testcontainers y Flyway
 * construye el esquema con las migraciones reales. Lo verificado es, por tanto,
 * el SQL que Hibernate emite sobre el esquema de producción y no una
 * aproximación H2.</p>
 *
 * <p>Las estadísticas de Hibernate se activan para poder contar sentencias: es
 * la única forma de demostrar que una consulta no degenera en N+1.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ActiveProfiles("test")
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
abstract class AbstractJpaSliceTest {

    /**
     * Propiedades de conexión que cada subclase registra desde su contenedor.
     *
     * <p>{@code spring.flyway.url} se fija explícitamente al contenedor: en
     * {@code application.properties} apunta a {@code DATABASE_DIRECT_URL}, y
     * sin este override un entorno con las variables de Neon exportadas haría
     * que Flyway migrase la base real desde los tests.</p>
     */
    protected static void registrarDatasource(DynamicPropertyRegistry registry, PostgreSQLContainer<?> contenedor) {
        registry.add("spring.datasource.url", contenedor::getJdbcUrl);
        registry.add("spring.datasource.username", contenedor::getUsername);
        registry.add("spring.datasource.password", contenedor::getPassword);
        registry.add("spring.flyway.url", contenedor::getJdbcUrl);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.generate_statistics", () -> "true");
    }

    @PersistenceContext
    protected EntityManager em;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    /** Vacía la sesión: lo que se mida después es lo que emite cada consulta. */
    protected void limpiarSesion() {
        em.flush();
        em.clear();
    }

    protected Statistics estadisticas() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }
}
