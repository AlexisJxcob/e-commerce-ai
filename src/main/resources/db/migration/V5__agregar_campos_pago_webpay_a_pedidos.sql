-- =====================================================================
-- V5: campos de pago Transbank Webpay Plus en pedidos
-- ---------------------------------------------------------------------
-- Registra el estado de pago del pedido y los identificadores de la
-- transaccion de Webpay (token de transaccion y codigo de autorizacion).
-- =====================================================================

ALTER TABLE pedidos
    ADD COLUMN IF NOT EXISTS webpay_token VARCHAR(255),
    ADD COLUMN IF NOT EXISTS webpay_authorization_code VARCHAR(50),
    ADD COLUMN IF NOT EXISTS estado_pago VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE';

ALTER TABLE pedidos
    DROP CONSTRAINT IF EXISTS uk_pedidos_webpay_token;

ALTER TABLE pedidos
    ADD CONSTRAINT uk_pedidos_webpay_token UNIQUE (webpay_token);

CREATE INDEX IF NOT EXISTS idx_pedidos_webpay_token ON pedidos(webpay_token);
