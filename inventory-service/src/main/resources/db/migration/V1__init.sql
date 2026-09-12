CREATE TABLE inventory (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL UNIQUE,
    quantity INTEGER NOT NULL,
    reserved INTEGER NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);
