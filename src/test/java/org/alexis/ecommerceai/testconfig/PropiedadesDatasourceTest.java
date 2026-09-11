package org.alexis.ecommerceai.testconfig;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Registra las propiedades de conexión de un contenedor de pruebas.
 *
 * <p>Existe para no repetir (ni olvidar) un detalle que ya rompió el build una
 * vez: en {@code application.properties}, {@code spring.datasource.url} sale de
 * {@code DATABASE_URL} y {@code spring.flyway.url} de {@code DATABASE_DIRECT_URL}.
 * Un test que sólo sobreescriba el datasource deja a Flyway apuntando a la base
 * de Neon del entorno, con dos consecuencias: migra la base real y el contenedor
 * se queda sin esquema ({@code Schema validation: missing table [...]}).</p>
 *
 * <p>Además, al fijar {@code spring.flyway.url} hay que fijar también sus
 * credenciales: Boot construye entonces un {@code SimpleDriverDataSource} con
 * las propiedades de Flyway y no hereda las del DataSource principal.</p>
 */
public final class PropiedadesDatasourceTest {

    private PropiedadesDatasourceTest() {
    }

    /** Apunta datasource y Flyway al contenedor, credenciales incluidas. */
    public static void registrar(DynamicPropertyRegistry registry, PostgreSQLContainer<?> contenedor) {
        registry.add("spring.datasource.url", contenedor::getJdbcUrl);
        registry.add("spring.datasource.username", contenedor::getUsername);
        registry.add("spring.datasource.password", contenedor::getPassword);
        registry.add("spring.flyway.url", contenedor::getJdbcUrl);
        registry.add("spring.flyway.user", contenedor::getUsername);
        registry.add("spring.flyway.password", contenedor::getPassword);
    }
}
