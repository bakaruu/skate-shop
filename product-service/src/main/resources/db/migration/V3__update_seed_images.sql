-- The V2 seed data pointed at external Unsplash hotlinks; two had gone dead (404) and the rest
-- didn't match their product's category. These are now real product photos bundled with the
-- frontend (public/products/), served relative to whatever origin hosts the Angular app rather
-- than depending on an external image host's uptime.
UPDATE products SET image_url = '/products/powell-peralta-skull-sword.jpg' WHERE name = 'Powell Peralta Skull & Sword';
UPDATE products SET image_url = '/products/independent-stage-11.jpg' WHERE name = 'Independent Stage 11';
UPDATE products SET image_url = '/products/spitfire-formula-four.jpg' WHERE name = 'Spitfire Formula Four';
UPDATE products SET image_url = '/products/santa-cruz-screaming-hand.jpg' WHERE name = 'Santa Cruz Screaming Hand';
UPDATE products SET image_url = '/products/element-section-complete.jpg' WHERE name = 'Element Section Complete';
UPDATE products SET image_url = '/products/thunder-149-hollow-light.jpg' WHERE name = 'Thunder 149 Hollow Light';
UPDATE products SET image_url = '/products/bones-stf-v1-52mm.jpg' WHERE name = 'Bones STF V1 52mm';
