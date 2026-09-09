-- =====================================================================
-- V1: Baseline del esquema inicial
-- ---------------------------------------------------------------------
-- Extensión pgvector + todas las tablas mapeadas por las entidades JPA
-- (productos, categorias, usuarios, pedidos, items_pedido, items_carrito).
--
-- REGLA DE INMUTABILIDAD: una vez mergeada, esta migración NO se edita.
-- Cualquier cambio de esquema posterior debe ser una migración nueva
-- (V2__..., V3__...) que evolucione este estado.
--
-- Nota sobre la extensión: si el usuario de la base no tiene privilegio
-- CREATE (p. ej. roles restringidos), la extensión debe crearse antes de
-- aplicar migraciones (CREATE EXTENSION vector) y esta sentencia es un
-- no-op idempotente.
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS vector;

-- ---------------------------------------------------------------------
-- categorias (Categoria.java)
-- ---------------------------------------------------------------------
CREATE TABLE categorias (
    id          BIGSERIAL PRIMARY KEY,
    nombre      VARCHAR(100) NOT NULL,
    descripcion TEXT,
    CONSTRAINT uk_categorias_nombre UNIQUE (nombre)
);

-- ---------------------------------------------------------------------
-- productos (Producto.java) — incluye la columna vector(384) de pgvector.
-- categoria_id es nullable (filas legacy) y coincide con el mapeo JPA;
-- su backfill es una migración posterior, nunca una edición de este file.
-- ---------------------------------------------------------------------
CREATE TABLE productos (
    id                    BIGSERIAL PRIMARY KEY,
    sku                   VARCHAR(50)  NOT NULL,
    nombre                VARCHAR(100) NOT NULL,
    descripcion_tecnica   TEXT,
    descripcion_coloquial TEXT,
    precio                NUMERIC(10, 2) NOT NULL,
    stock                 INTEGER      NOT NULL,
    embedding             vector(384),
    version               BIGINT       NOT NULL DEFAULT 0,
    categoria_id          BIGINT,
    CONSTRAINT uk_productos_sku UNIQUE (sku),
    CONSTRAINT fk_productos_categoria FOREIGN KEY (categoria_id)
        REFERENCES categorias (id)
);

-- ---------------------------------------------------------------------
-- usuarios (Usuario.java)
-- ---------------------------------------------------------------------
CREATE TABLE usuarios (
    id       BIGSERIAL PRIMARY KEY,
    username VARCHAR(50)  NOT NULL,
    password VARCHAR(100) NOT NULL,
    rol      VARCHAR(20)  NOT NULL,
    CONSTRAINT uk_usuarios_username UNIQUE (username)
);

-- ---------------------------------------------------------------------
-- pedidos (Pedido.java)
-- ---------------------------------------------------------------------
CREATE TABLE pedidos (
    id             BIGSERIAL PRIMARY KEY,
    usuario_id     BIGINT        NOT NULL,
    fecha_creacion TIMESTAMP     NOT NULL,
    estado         VARCHAR(20)   NOT NULL,
    total          NUMERIC(10, 2) NOT NULL,
    CONSTRAINT fk_pedidos_usuario FOREIGN KEY (usuario_id)
        REFERENCES usuarios (id)
);

-- ---------------------------------------------------------------------
-- items_pedido (ItemPedido.java) — producto_id es columna simple
-- (snapshot, sin FK) para que el catálogo no altere pedidos históricos.
-- ---------------------------------------------------------------------
CREATE TABLE items_pedido (
    id             BIGSERIAL PRIMARY KEY,
    pedido_id      BIGINT         NOT NULL,
    producto_id    BIGINT         NOT NULL,
    cantidad       INTEGER        NOT NULL,
    precio_unitario NUMERIC(10, 2) NOT NULL,
    CONSTRAINT fk_items_pedido_pedido FOREIGN KEY (pedido_id)
        REFERENCES pedidos (id)
);

-- ---------------------------------------------------------------------
-- items_carrito (ItemCarrito.java) — un (usuario, producto) por línea;
-- si se borra un producto, sus líneas de carrito se purgan (ON DELETE
-- CASCADE, coherente con @OnDelete de la entidad).
-- ---------------------------------------------------------------------
CREATE TABLE items_carrito (
    id           BIGSERIAL PRIMARY KEY,
    usuario_id   BIGINT  NOT NULL,
    producto_id  BIGINT  NOT NULL,
    cantidad     INTEGER NOT NULL,
    CONSTRAINT fk_items_carrito_usuario FOREIGN KEY (usuario_id)
        REFERENCES usuarios (id),
    CONSTRAINT fk_items_carrito_producto FOREIGN KEY (producto_id)
        REFERENCES productos (id) ON DELETE CASCADE,
    CONSTRAINT uk_items_carrito_usuario_producto UNIQUE (usuario_id, producto_id)
);
