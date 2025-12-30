-- V3__outbox_events_author_uuid.sql
-- Objetivo:
-- 1) Renomear public.outbox_events.author_id (UUID) -> author_uuid
-- 2) Criar índice em author_uuid

DO $$
BEGIN
  -- Renomeia apenas se a tabela/coluna existirem e ainda estiver com nome antigo
  IF EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name   = 'outbox_events'
      AND column_name  = 'author_id'
  ) THEN
    -- Sanity: só renomeia se o tipo for uuid (evita renomear coluna errada)
    IF EXISTS (
      SELECT 1
      FROM information_schema.columns
      WHERE table_schema = 'public'
        AND table_name   = 'outbox_events'
        AND column_name  = 'author_id'
        AND data_type    = 'uuid'
    ) THEN
      EXECUTE 'ALTER TABLE public.outbox_events RENAME COLUMN author_id TO author_uuid';
ELSE
      RAISE EXCEPTION 'outbox_events.author_id existe mas nao eh UUID; abortando V3 para evitar inconsistência.';
END IF;
END IF;

  -- Cria o índice apenas se a coluna author_uuid existir
  IF EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name   = 'outbox_events'
      AND column_name  = 'author_uuid'
  ) THEN
    EXECUTE 'CREATE INDEX IF NOT EXISTS idx_outbox_events_author_uuid ON public.outbox_events(author_uuid)';
END IF;
END $$;
