-- =====================================================================
-- V3: carrito persistente (carritos + carrito_items) e índices de FK
-- ---------------------------------------------------------------------
-- CONTEXTO IMPORTANTE: V1 ya creó `pedidos` e `items_pedido` (entidad
-- `ItemPedido` → tabla `items_pedido`), así que aquí NO se recrean ni se
-- renombran a `pedido_items`: renombrar rompería `ddl-auto=validate`.
-- Lo que aporta V3 a esas tablas es lo que les faltaba: índices en sus FK.
--
-- Cambios:
--   1. CREATE TABLE carritos      (cabecera del agregado, 1 por usuario)
--   2. CREATE TABLE carrito_items (líneas, UNIQUE carrito+producto,
--                                  FK a productos con ON DELETE CASCADE)
--   3. Migración de los datos legacy de `items_carrito` (agrupados por
--      usuario) ANTES de borrar la tabla.
--   4. DROP TABLE items_carrito   (reemplazada por el agregado nuevo).
--   5. Índices explícitos en las columnas FK.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1) carritos — cabecera del agregado (Carrito.java)
-- ---------------------------------------------------------------------
CREATE TABLE carritos (
    id         BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL,
    CONSTRAINT uk_carritos_usuario UNIQUE (usuario_id),
    CONSTRAINT fk_carritos_usuario FOREIGN KEY (usuario_id)
        REFERENCES usuarios (id)
);

-- ---------------------------------------------------------------------
-- 2) carrito_items — líneas del carrito (CarritoItem.java)
-- ---------------------------------------------------------------------
CREATE TABLE carrito_items (
    id          BIGSERIAL PRIMARY KEY,
    carrito_id  BIGINT  NOT NULL,
    producto_id BIGINT  NOT NULL,
    cantidad    INTEGER NOT NULL,
    CONSTRAINT uk_carrito_items_carrito_producto UNIQUE (carrito_id, producto_id),
    CONSTRAINT fk_carrito_items_carrito FOREIGN KEY (carrito_id)
        REFERENCES carritos (id),
    CONSTRAINT fk_carrito_items_producto FOREIGN KEY (producto_id)
        REFERENCES productos (id) ON DELETE CASCADE
);

-- ---------------------------------------------------------------------
-- 3) Migración de datos legacy: items_carrito (usuario, producto) →
--    carritos (por usuario) + carrito_items (por línea).
-- ---------------------------------------------------------------------
INSERT INTO carritos (usuario_id)
SELECT DISTINCT usuario_id FROM items_carrito;

INSERT INTO carrito_items (carrito_id, producto_id, cantidad)
SELECT c.id, ic.producto_id, ic.cantidad
FROM items_carrito ic
JOIN carritos c ON c.usuario_id = ic.usuario_id;

-- ---------------------------------------------------------------------
-- 4) La tabla legacy deja de existir: el carrito ya es un agregado.
-- ---------------------------------------------------------------------
DROP TABLE items_carrito;

-- ---------------------------------------------------------------------
-- 5) Índices sobre columnas FK.
--    No se duplican índices donde ya existe uno cuya PRIMERA columna es la
--    FK (PostgreSQL los usa igual para integridad referencial y joins):
--      - carritos.usuario_id        → cubierto por uk_carritos_usuario
--      - carrito_items.carrito_id   → cubierto por uk_carrito_items_carrito_producto
--    Los que siguen no tenían ningún índice.
-- ---------------------------------------------------------------------
CREATE INDEX idx_carrito_items_producto ON carrito_items (producto_id);
CREATE INDEX idx_productos_categoria    ON productos (categoria_id);
CREATE INDEX idx_pedidos_usuario        ON pedidos (usuario_id);
CREATE INDEX idx_items_pedido_pedido    ON items_pedido (pedido_id);
