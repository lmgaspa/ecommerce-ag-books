-- Ajustar tipos de colunas para TEXT e NUMERIC(38,2),
-- equivalente ao que o Hibernate estava aplicando com ddl-auto=update.

ALTER TABLE IF EXISTS books
    ALTER COLUMN description SET DATA TYPE TEXT;

ALTER TABLE IF EXISTS coupons
    ALTER COLUMN description SET DATA TYPE TEXT;

ALTER TABLE IF EXISTS coupons
    ALTER COLUMN discount_value SET DATA TYPE NUMERIC(38,2);

ALTER TABLE IF EXISTS coupons
    ALTER COLUMN maximum_discount_value SET DATA TYPE NUMERIC(38,2);

ALTER TABLE IF EXISTS coupons
    ALTER COLUMN minimum_order_value SET DATA TYPE NUMERIC(38,2);

ALTER TABLE IF EXISTS order_coupons
    ALTER COLUMN discount_amount SET DATA TYPE NUMERIC(38,2);

ALTER TABLE IF EXISTS order_coupons
    ALTER COLUMN final_total SET DATA TYPE NUMERIC(38,2);

ALTER TABLE IF EXISTS order_coupons
    ALTER COLUMN original_total SET DATA TYPE NUMERIC(38,2);

ALTER TABLE IF EXISTS orders
    ALTER COLUMN note SET DATA TYPE TEXT;

ALTER TABLE IF EXISTS orders
    ALTER COLUMN qr_code SET DATA TYPE TEXT;

ALTER TABLE IF EXISTS orders
    ALTER COLUMN qr_code_base64 SET DATA TYPE TEXT;

ALTER TABLE IF EXISTS webhook_events
    ALTER COLUMN raw_body SET DATA TYPE TEXT;
