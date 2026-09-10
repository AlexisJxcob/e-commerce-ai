-- =====================================================================
-- V4: email del usuario
-- ---------------------------------------------------------------------
-- CONTEXTO: el prompt del Bloque 4 (Paso 3) exige validación Jakarta
-- `@Email` sobre el registro. V1 creó `usuarios` sin columna `email`, así
-- que la validación no tenía dónde aplicarse: esta migración la añade.
--
-- DECISIÓN DE DISEÑO (declarada): la columna es NULLABLE y el email es
-- OPCIONAL en el registro, para no romper los clientes existentes ni el
-- admin sembrado por `AdminSeeder`. Cuando viene informado se valida y es
-- único a nivel de base de datos. PostgreSQL permite múltiples NULL en una
-- restricción UNIQUE, así que los usuarios sin email no colisionan.
-- =====================================================================

ALTER TABLE usuarios ADD COLUMN email VARCHAR(150);

ALTER TABLE usuarios
    ADD CONSTRAINT uk_usuarios_email UNIQUE (email);
