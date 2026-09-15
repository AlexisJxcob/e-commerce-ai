-- =====================================================================
-- V5: campos de pago Stripe en pedidos
-- ---------------------------------------------------------------------
-- Registra el estado de pago del pedido y los identificadores de la sesion
-- de Stripe Checkout y el PaymentIntent generado.
-- =====================================================================

ALTER TABLE pedidos
    ADD COLUMN IF NOT EXISTS stripe_session_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS stripe_payment_intent_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS estado_pago VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE';

ALTER TABLE pedidos
    DROP CONSTRAINT IF EXISTS uk_pedidos_stripe_session_id;

ALTER TABLE pedidos
    ADD CONSTRAINT uk_pedidos_stripe_session_id UNIQUE (stripe_session_id);

CREATE INDEX IF NOT EXISTS idx_pedidos_stripe_session ON pedidos(stripe_session_id);
