-- R__author_registry_uuid_default.sql
-- Define default seguro para author_uuid e preenche nulos (idempotente)

-- Garante a extensão para gen_random_uuid()
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pgcrypto') THEN
    CREATE EXTENSION IF NOT EXISTS pgcrypto;
  END IF;
END$$;

DO $$
BEGIN
  IF to_regclass('public.payment_author_registry') IS NOT NULL
     AND EXISTS (
       SELECT 1
       FROM information_schema.columns
       WHERE table_schema='public'
         AND table_name='payment_author_registry'
         AND column_name='author_uuid'
     )
  THEN
    -- default (resiliente)
    BEGIN
      ALTER TABLE public.payment_author_registry
        ALTER COLUMN author_uuid SET DEFAULT gen_random_uuid();
    EXCEPTION WHEN others THEN
      -- Se alguém já definiu default diferente, não quebra
      NULL;
    END;

    -- preencher nulos (se houver)
    UPDATE public.payment_author_registry
       SET author_uuid = gen_random_uuid()
     WHERE author_uuid IS NULL;
  END IF;
END$$;
