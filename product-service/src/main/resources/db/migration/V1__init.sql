CREATE TABLE products (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    brand VARCHAR(255) NOT NULL,
    category VARCHAR(50) NOT NULL,
    price NUMERIC(19, 2) NOT NULL,
    description VARCHAR(1000),
    image_url VARCHAR(500),
    width DOUBLE PRECISION,
    active BOOLEAN NOT NULL DEFAULT TRUE
);
