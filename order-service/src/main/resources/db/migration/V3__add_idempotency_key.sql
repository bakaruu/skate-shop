ALTER TABLE orders ADD COLUMN idempotency_key VARCHAR(64);

-- Partial unique index: only non-null keys must be unique, so requests that
-- don't send one (or older rows created before this column existed) never collide.
CREATE UNIQUE INDEX idx_orders_idempotency_key ON orders (idempotency_key) WHERE idempotency_key IS NOT NULL;
