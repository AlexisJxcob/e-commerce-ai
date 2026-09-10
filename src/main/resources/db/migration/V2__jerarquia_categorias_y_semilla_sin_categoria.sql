-- =====================================================================
-- V2: jerarquía de categorías + semilla "Sin categoría" + backfill
-- ---------------------------------------------------------------------
-- CONTEXTO IMPORTANTE: V1 ya creó `usuarios`, `categorias` y la columna
-- `productos.categoria_id` (con su FK). Por eso esta migración NO vuelve a
-- crearlas: es estrictamente evolutiva.
--
-- Cambios:
--   1. `categorias.padre_id` (self-FK, nullable) → jerarquía de categorías,
--      mapeada por `Categoria.padre` (@ManyToOne LAZY).
--   2. Índice sobre `categorias.padre_id` (toda FK consultada por padre).
--   3. Fila semilla "Sin categoría" (bucket para productos sin categoría).
--   4. Backfill defensivo de `productos.categoria_id IS NULL` hacia esa
--      semilla, solo si la tabla `productos` tiene filas.
--
-- DESVIACIÓN DECLARADA respecto al prompt del Bloque 4: el prompt pedía una
-- columna `slug` en la semilla. `categorias` no tiene `slug` y `Categoria`
-- no lo mapea; añadir una columna muerta queda fuera del alcance del bloque,
-- así que la semilla se identifica por `nombre` (que ya es UNIQUE).
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1) Jerarquía
-- ---------------------------------------------------------------------
ALTER TABLE categorias ADD COLUMN padre_id BIGINT;

ALTER TABLE categorias
    ADD CONSTRAINT fk_categorias_padre FOREIGN KEY (padre_id)
        REFERENCES categorias (id);

CREATE INDEX idx_categorias_padre ON categorias (padre_id);

-- ---------------------------------------------------------------------
-- 2) Semilla + backfill defensivo
--    Un solo bloque DO para que la comprobación previa sea verificable en
--    el log de Flyway (RAISE NOTICE) y no solo implícita en el WHERE.
-- ---------------------------------------------------------------------
DO $$
DECLARE
    v_semilla_id      BIGINT;
    v_total_productos BIGINT;
    v_sin_categoria   BIGINT;
BEGIN
    -- Semilla idempotente: si ya existe por nombre, se reutiliza.
    SELECT id INTO v_semilla_id FROM categorias WHERE nombre = 'Sin categoría';
    IF v_semilla_id IS NULL THEN
        INSERT INTO categorias (nombre, descripcion)
        VALUES ('Sin categoría', 'Bucket por defecto para productos sin categoría asignada')
        RETURNING id INTO v_semilla_id;
        RAISE NOTICE 'V2: semilla "Sin categoría" creada con id=%', v_semilla_id;
    ELSE
        RAISE NOTICE 'V2: semilla "Sin categoría" ya existía con id=%', v_semilla_id;
    END IF;

    -- Comprobación defensiva antes del UPDATE (requisito explícito).
    SELECT COUNT(*) INTO v_total_productos FROM productos;
    SELECT COUNT(*) INTO v_sin_categoria
      FROM productos WHERE categoria_id IS NULL;

    IF v_total_productos > 0 AND v_sin_categoria > 0 THEN
        UPDATE productos SET categoria_id = v_semilla_id WHERE categoria_id IS NULL;
        RAISE NOTICE 'V2: backfill aplicado a % producto(s) sin categoría', v_sin_categoria;
    ELSE
        RAISE NOTICE 'V2: backfill omitido (% producto(s) en total, % sin categoría)',
            v_total_productos, v_sin_categoria;
    END IF;
END $$;
