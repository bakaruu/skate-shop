-- Defense-in-depth: these invariants are already enforced in application code
-- (InventoryService), but a CHECK constraint makes a violation fail loudly at the database
-- instead of silently persisting corrupted data if a future code path skips the checks.
ALTER TABLE inventory
    ADD CONSTRAINT chk_inventory_quantity_non_negative CHECK (quantity >= 0),
    ADD CONSTRAINT chk_inventory_reserved_non_negative CHECK (reserved >= 0),
    ADD CONSTRAINT chk_inventory_reserved_not_over_quantity CHECK (reserved <= quantity);
