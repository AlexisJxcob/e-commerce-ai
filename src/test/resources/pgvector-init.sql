-- Habilitar la extensión pgvector en la base de datos del contenedor de test.
-- La imagen pgvector/pgvector instala el binario, pero la extensión debe
-- crearse explícitamente antes de que Hibernate genere el esquema.
CREATE EXTENSION IF NOT EXISTS vector;
