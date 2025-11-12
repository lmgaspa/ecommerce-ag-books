-- R__author_registry_uuid_default.sql
-- Define default seguro para author_uuid e preenche nulos (idempotente)

DO $$
BEGIN
  IF to_regclass('public.payment_author_registry') IS NOT NULL
     AND EXISTS (
       SELECT 1 FROM information_schema.columns
       WHERE table_schema='public' AND table_name='payment_author_registry' AND column_name='author_uuid'
     )
  THEN
    -- default
    BEGIN
      ALTER TABLE payment_author_registry
        ALTER COLUMN author_uuid SET DEFAULT gen_random_uuid();
    EXCEPTION WHEN others THEN
      NULL;
    END;

    -- preencher nulos (se houver)
    UPDATE payment_author_registry
       SET author_uuid = gen_random_uuid()
     WHERE author_uuid IS NULL;
  END IF;
END$$;
