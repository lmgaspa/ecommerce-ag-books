-- V20251122__authors_hardening_fix.sql
-- Objetivo: aplicar endurecimentos no authors sem conflitar com a coluna gerada email_lower
-- Safe/idempotente: checa existência antes de criar/dropar

-- 1) Garantir que a tabela authors existe
DO $$
BEGIN
  IF to_regclass('public.authors') IS NULL THEN
    RAISE NOTICE 'Tabela "authors" não existe. Nada a fazer.';
    RETURN;
  END IF;
END$$;

-- 2) Remover UNIQUE/índices antigos conflitantes (se houver)
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM pg_indexes
    WHERE schemaname='public' AND indexname='ux_authors_email_lower'
  ) THEN
    EXECUTE 'DROP INDEX ux_authors_email_lower';
  END IF;

  -- Caso exista constraint UNIQUE antiga sobre lower(email)
  IF EXISTS (
    SELECT 1
    FROM pg_constraint c
    JOIN pg_class t ON t.oid = c.conrelid
    WHERE t.relname = 'authors'
      AND c.contype = 'u'
      AND pg_get_constraintdef(c.oid) ILIKE '%lower(email)%'
  ) THEN
    EXECUTE (
      SELECT 'ALTER TABLE authors DROP CONSTRAINT ' || quote_ident(c.conname)
      FROM pg_constraint c
      JOIN pg_class t ON t.oid = c.conrelid
      WHERE t.relname = 'authors'
        AND c.contype = 'u'
        AND pg_get_constraintdef(c.oid) ILIKE '%lower(email)%'
      LIMIT 1
    );
  END IF;
END$$;

-- 3) Dropar temporariamente a coluna gerada email_lower (se existir), para poder ajustar email
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema='public' AND table_name='authors' AND column_name='email_lower'
  ) THEN
    EXECUTE 'ALTER TABLE authors DROP COLUMN email_lower';
  END IF;
END$$;

-- 4) Ajustes na coluna email (tipo/tamanho/nullability) — customize conforme sua regra
-- Exemplo: garantir varchar(255) NOT NULL e normalizar espaços
ALTER TABLE authors
  ALTER COLUMN email TYPE varchar(255);

-- Se quiser garantir NOT NULL de forma segura (só se já estiver sem nulos):
-- UPDATE authors SET email = trim(email);
-- ALTER TABLE authors ALTER COLUMN email SET NOT NULL;

-- 5) Recriar a coluna GERADA email_lower
ALTER TABLE authors
  ADD COLUMN IF NOT EXISTS email_lower text GENERATED ALWAYS AS (lower(email)) STORED;

-- 6) Índice único em email_lower (sem CONCURRENTLY por conta da transação do Flyway)
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_indexes
    WHERE schemaname='public' AND indexname='ux_authors_email_lower'
  ) THEN
    EXECUTE 'CREATE UNIQUE INDEX ux_authors_email_lower ON authors (email_lower)';
  END IF;
END$$;
