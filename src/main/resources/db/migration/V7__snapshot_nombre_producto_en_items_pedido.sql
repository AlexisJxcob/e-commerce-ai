-- =====================================================================
-- V7: snapshot del nombre del producto en las líneas de pedido
-- ---------------------------------------------------------------------
-- El comprobante mostraba "Producto #<id>". Guardar el nombre al momento
-- de la compra permite mostrar el detalle real sin depender del catálogo
-- actual (el producto pudo renombrarse o eliminarse).
-- =====================================================================

ALTER TABLE items_pedido
    ADD COLUMN IF NOT EXISTS producto_nombre VARCHAR(100);

-- Backfill defensivo desde el catálogo para los pedidos ya existentes.
UPDATE items_pedido ip
SET producto_nombre = p.nombre
FROM productos p
WHERE ip.producto_id = p.id
  AND ip.producto_nombre IS NULL;
