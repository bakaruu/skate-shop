INSERT INTO inventory (product_id, quantity, reserved)
SELECT 1, 10, 0 WHERE NOT EXISTS (SELECT 1 FROM inventory WHERE product_id = 1);

INSERT INTO inventory (product_id, quantity, reserved)
SELECT 2, 10, 0 WHERE NOT EXISTS (SELECT 1 FROM inventory WHERE product_id = 2);

INSERT INTO inventory (product_id, quantity, reserved)
SELECT 3, 10, 0 WHERE NOT EXISTS (SELECT 1 FROM inventory WHERE product_id = 3);

INSERT INTO inventory (product_id, quantity, reserved)
SELECT 4, 10, 0 WHERE NOT EXISTS (SELECT 1 FROM inventory WHERE product_id = 4);

INSERT INTO inventory (product_id, quantity, reserved)
SELECT 5, 10, 0 WHERE NOT EXISTS (SELECT 1 FROM inventory WHERE product_id = 5);

INSERT INTO inventory (product_id, quantity, reserved)
SELECT 6, 10, 0 WHERE NOT EXISTS (SELECT 1 FROM inventory WHERE product_id = 6);

INSERT INTO inventory (product_id, quantity, reserved)
SELECT 7, 10, 0 WHERE NOT EXISTS (SELECT 1 FROM inventory WHERE product_id = 7);