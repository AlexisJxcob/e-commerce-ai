package org.alexis.ecommerceai;

import org.alexis.ecommerceai.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

/**
 * Verifica que el contexto de Spring arranca completo
 * (con PostgreSQL+pgvector vía Testcontainers y sin dependencias externas).
 */
class ECommerceAiApplicationTests extends AbstractIntegrationTest {

    @Test
    void contextLoads() {
    }

}
