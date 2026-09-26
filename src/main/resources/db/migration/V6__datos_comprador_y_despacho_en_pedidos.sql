-- =====================================================================
-- V6: datos de comprador y entrega + desglose de despacho en pedidos
-- ---------------------------------------------------------------------
-- Antes el checkout recolectaba nombre, RUT, correo, teléfono y dirección
-- y los descartaba: el pedido no era despachable y el costo de despacho se
-- calculaba sólo en el navegador. Estas columnas persisten esos datos y el
-- desglose (subtotal + despacho = total) que el servidor cobra.
--
-- Todas las columnas son aditivas y con default, para que los pedidos
-- existentes sigan siendo válidos.
-- =====================================================================

ALTER TABLE pedidos
    ADD COLUMN IF NOT EXISTS subtotal NUMERIC(10, 2),
    ADD COLUMN IF NOT EXISTS costo_despacho NUMERIC(10, 2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS metodo_entrega VARCHAR(20),
    ADD COLUMN IF NOT EXISTS comprador_nombre VARCHAR(70),
    ADD COLUMN IF NOT EXISTS comprador_apellidos VARCHAR(70),
    ADD COLUMN IF NOT EXISTS comprador_rut VARCHAR(20),
    ADD COLUMN IF NOT EXISTS comprador_email VARCHAR(120),
    ADD COLUMN IF NOT EXISTS comprador_telefono VARCHAR(25),
    ADD COLUMN IF NOT EXISTS despacho_region VARCHAR(100),
    ADD COLUMN IF NOT EXISTS despacho_comuna VARCHAR(100),
    ADD COLUMN IF NOT EXISTS despacho_direccion VARCHAR(200),
    ADD COLUMN IF NOT EXISTS despacho_depto VARCHAR(100),
    ADD COLUMN IF NOT EXISTS despacho_referencias VARCHAR(300),
    ADD COLUMN IF NOT EXISTS retira_tercero BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS tercero_nombre VARCHAR(150),
    ADD COLUMN IF NOT EXISTS tercero_rut VARCHAR(20);

-- Backfill: los pedidos previos a V6 cobraron solo productos.
UPDATE pedidos SET subtotal = total WHERE subtotal IS NULL;

ALTER TABLE pedidos
    DROP CONSTRAINT IF EXISTS ck_pedidos_metodo_entrega;

ALTER TABLE pedidos
    ADD CONSTRAINT ck_pedidos_metodo_entrega
        CHECK (metodo_entrega IS NULL OR metodo_entrega IN ('RETIRO', 'DESPACHO'));
