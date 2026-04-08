INSERT INTO products (name, brand, category, price, description, image_url, width, active)
SELECT 'Powell Peralta Skull & Sword', 'Powell Peralta', 'DECK', 89.99, 'Classic reissue deck, old school shape', 'https://images.unsplash.com/photo-1547447134-cd3f5c716030?w=400&h=400&fit=crop', 9.0, true
    WHERE NOT EXISTS (SELECT 1 FROM products WHERE name = 'Powell Peralta Skull & Sword');

INSERT INTO products (name, brand, category, price, description, image_url, width, active)
SELECT 'Independent Stage 11', 'Independent', 'TRUCKS', 59.99, 'The most popular truck in skateboarding', 'https://images.unsplash.com/photo-1564415315949-7a0c4c73aab4?w=400&h=400&fit=crop', 8.25, true
    WHERE NOT EXISTS (SELECT 1 FROM products WHERE name = 'Independent Stage 11');

INSERT INTO products (name, brand, category, price, description, image_url, width, active)
SELECT 'Spitfire Formula Four', 'Spitfire', 'WHEELS', 44.99, 'High performance urethane wheels', 'https://images.unsplash.com/photo-1520045892732-304bc3ac5d8e?w=400&h=400&fit=crop', 0.0, true
    WHERE NOT EXISTS (SELECT 1 FROM products WHERE name = 'Spitfire Formula Four');

INSERT INTO products (name, brand, category, price, description, image_url, width, active)
SELECT 'Santa Cruz Screaming Hand', 'Santa Cruz', 'DECK', 79.99, 'Iconic Santa Cruz graphic deck', 'https://images.unsplash.com/photo-1453395874897-98b35aa0de8c?w=400&h=400&fit=crop', 8.5, true
    WHERE NOT EXISTS (SELECT 1 FROM products WHERE name = 'Santa Cruz Screaming Hand');

INSERT INTO products (name, brand, category, price, description, image_url, width, active)
SELECT 'Element Section Complete', 'Element', 'DECK', 119.99, 'Complete skateboard for beginners', 'https://images.unsplash.com/photo-1491553895911-0055eca6402d?w=400&h=400&fit=crop', 8.0, true
    WHERE NOT EXISTS (SELECT 1 FROM products WHERE name = 'Element Section Complete');

INSERT INTO products (name, brand, category, price, description, image_url, width, active)
SELECT 'Thunder 149 Hollow Light', 'Thunder', 'TRUCKS', 64.99, 'Lightweight hollow kingpin and axle trucks', 'https://images.unsplash.com/photo-1542291026-7eec264c27ff?w=400&h=400&fit=crop', 8.25, true
    WHERE NOT EXISTS (SELECT 1 FROM products WHERE name = 'Thunder 149 Hollow Light');

INSERT INTO products (name, brand, category, price, description, image_url, width, active)
SELECT 'Bones STF V1 52mm', 'Bones', 'WHEELS', 39.99, 'Street Tech Formula wheels, super smooth', 'https://images.unsplash.com/photo-1556906781-9a412961a679?w=400&h=400&fit=crop', 0.0, true
    WHERE NOT EXISTS (SELECT 1 FROM products WHERE name = 'Bones STF V1 52mm');