-- Optimistic locking so a background expiry sweep (OrderExpiryScheduler) can never race a
-- concurrent payment-webhook update to the same order row without one of them failing loudly.
ALTER TABLE orders ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
