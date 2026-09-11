package org.alexis.ecommerceai.integration;

import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Resuelve contra qué Postgres corre la suite de integración.
 *
 * <p>Dos modos, elegidos por entorno y nunca por código:</p>
 * <ol>
 *   <li><b>Neon</b> — si {@code NEON_TEST_DATABASE_URL} está definida, la suite
 *       completa corre contra esa rama Neon de pruebas. No se levanta ningún
 *       contenedor, así que el modo Neon tampoco depende de Docker.</li>
 *   <li><b>Testcontainers</b> — por defecto, con la imagen
 *       {@code pgvector/pgvector:pg16}, que es como corría la suite antes.</li>
 * </ol>
 *
 * <p>El contenedor se arranca de forma perezosa: sólo se materializa cuando
 * alguien pide sus propiedades de conexión, de modo que el modo Neon no paga
 * (ni exige) el arranque de Docker.</p>
 */
final class EntornoIntegracion {

    private static final String URL_PRUEBAS = System.getenv("NEON_TEST_DATABASE_URL");
    private static final String URL_DIRECTA_PRUEBAS = System.getenv("NEON_TEST_DATABASE_DIRECT_URL");
    private static final String USUARIO_PRUEBAS = System.getenv("NEON_TEST_DATABASE_USER");
    private static final String PASSWORD_PRUEBAS = System.getenv("NEON_TEST_DATABASE_PASSWORD");

    private static PostgreSQLContainer<?> contenedor;

    private EntornoIntegracion() {
    }

    /** true si la suite debe apuntar a una rama Neon en lugar de a Testcontainers. */
    static boolean usarNeon() {
        return URL_PRUEBAS != null && !URL_PRUEBAS.isBlank();
    }

    /** Calcula si la suite puede ejecutarse: hay Neon configurado o hay Docker. */
    static boolean disponible() {
        return usarNeon() || DockerClientFactory.instance().isDockerAvailable();
    }

    static String urlPooled() {
        return URL_PRUEBAS;
    }

    /** Flyway nunca migra a través del pooler (PgBouncer no soporta estado de sesión). */
    static String urlDirecta() {
        return URL_DIRECTA_PRUEBAS != null && !URL_DIRECTA_PRUEBAS.isBlank() ? URL_DIRECTA_PRUEBAS : URL_PRUEBAS;
    }

    static String usuario() {
        return USUARIO_PRUEBAS;
    }

    static String password() {
        return PASSWORD_PRUEBAS;
    }

    /** Contenedor PostgreSQL+pgvector compartido; {@code null} en modo Neon. */
    static synchronized PostgreSQLContainer<?> contenedor() {
        if (usarNeon()) {
            return null;
        }
        if (contenedor == null) {
            contenedor = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
                    .withDatabaseName("ecommerce_test")
                    .withUsername("test")
                    .withPassword("test")
                    .withInitScript("pgvector-init.sql");
            contenedor.start();
        }
        return contenedor;
    }
}
