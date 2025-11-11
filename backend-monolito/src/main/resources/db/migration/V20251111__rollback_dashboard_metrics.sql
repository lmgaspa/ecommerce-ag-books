DO
$$
BEGIN
    ALTER TABLE orders DROP CONSTRAINT IF EXISTS chk_orders_shipment_status;
EXCEPTION
    WHEN undefined_table THEN NULL;
END
$$;

DROP INDEX IF EXISTS idx_orders_author_paid_at;
DROP INDEX IF EXISTS idx_orders_author_status;
DROP INDEX IF EXISTS idx_orders_author_shipment_status;
DROP INDEX IF EXISTS idx_orders_author_state;

ALTER TABLE orders
    DROP COLUMN IF EXISTS author_id,
    DROP COLUMN IF EXISTS gross_amount,
    DROP COLUMN IF EXISTS net_amount,
    DROP COLUMN IF EXISTS channel,
    DROP COLUMN IF EXISTS customer_short_name,
    DROP COLUMN IF EXISTS created_at,
    DROP COLUMN IF EXISTS updated_at,
    DROP COLUMN IF EXISTS shipment_status;

DROP TABLE IF EXISTS payments;
DROP TABLE IF EXISTS shipments;

