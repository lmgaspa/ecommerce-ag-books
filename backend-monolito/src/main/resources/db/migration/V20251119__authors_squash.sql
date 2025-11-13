-- V20251119__authors_squash.sql
-- Consolidação incremental da trilha de AUTHORS para bancos JÁ EXISTENTES.
-- Objetivo: estado final canônico, sem duplicatas e com índice único estável.

-- 0) Pré-checagem suave (tabela deve existir em bancos já rodando)
DO $$
BEGIN
  IF to_regclass('public.authors') IS NULL THEN
    RAISE NOTICE 'Tabela public.authors não existe; nada a fazer nesta migração.';
    RETURN;
  END IF;
END$$;

-- 1) Garantir created_at com DEFAULT e NOT NULL (preenche nulos antes)
ALTER TABLE public.authors
  ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ;

UPDATE public.authors
   SET created_at = NOW()
 WHERE created_at IS NULL;

DO $$
BEGIN
  BEGIN
    ALTER TABLE public.authors
      ALTER COLUMN created_at SET DEFAULT NOW();
  EXCEPTION WHEN others THEN
    NULL;
  END;

  BEGIN
    ALTER TABLE public.authors
      ALTER COLUMN created_at SET NOT NULL;
  EXCEPTION WHEN others THEN
    NULL;
  END;
END$$;

-- 2) Deduplicação por lower(email) preservando menor id
WITH dup AS (
  SELECT id
  FROM (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY lower(email) ORDER BY id) AS rn
    FROM public.authors
  ) t
  WHERE t.rn > 1
)
DELETE FROM public.authors a
USING dup d
WHERE a.id = d.id;

-- 3) Normalizar coluna email_lower como GENERATED ALWAYS
--    Casos a tratar:
--    a) Coluna não existe  -> criar como GENERATED ALWAYS
--    b) Coluna existe e JÁ é GENERATED ALWAYS -> manter
--    c) Coluna existe mas NÃO é GENERATED -> dropar + recriar como GENERATED ALWAYS
DO $$
DECLARE
  v_is_generated text;
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
     WHERE table_schema='public' AND table_name='authors' AND column_name='email_lower'
  ) THEN
    EXECUTE 'ALTER TABLE public.authors ADD COLUMN email_lower TEXT GENERATED ALWAYS AS (lower(email)) STORED';
  ELSE
    SELECT is_generated
      INTO v_is_generated
      FROM information_schema.columns
     WHERE table_schema='public' AND table_name='authors' AND column_name='email_lower';

    IF v_is_generated IS DISTINCT FROM 'ALWAYS' THEN
      -- dropar índices dependentes de email_lower (se houver)
      PERFORM 1
      FROM pg_indexes
     WHERE schemaname='public' AND tablename='authors' AND indexname='uq_authors_email_lower';
      IF FOUND THEN
        EXECUTE 'DROP INDEX IF EXISTS public.uq_authors_email_lower';
      END IF;

      -- trocar a coluna por versão GENERATED ALWAYS
      EXECUTE 'ALTER TABLE public.authors DROP COLUMN email_lower';
      EXECUTE 'ALTER TABLE public.authors ADD COLUMN email_lower TEXT GENERATED ALWAYS AS (lower(email)) STORED';
    END IF;
  END IF;
END$$;

-- 4) Índice único canônico em email_lower
--    - cria o canônico
--    - remove índices antigos/duplicados (ex.: ux_authors_email_lower, índices em lower(email))
DO $$
DECLARE
  v_old_idx text;
BEGIN
  -- cria o canônico se não existir
  IF NOT EXISTS (
    SELECT 1 FROM pg_indexes
    WHERE schemaname='public' AND tablename='authors' AND indexname='uq_authors_email_lower'
  ) THEN
    EXECUTE 'CREATE UNIQUE INDEX uq_authors_email_lower ON public.authors (email_lower)';
  END IF;

  -- remover índice antigo com nome "ux_authors_email_lower"
  IF EXISTS (
    SELECT 1 FROM pg_indexes
    WHERE schemaname='public' AND indexname='ux_authors_email_lower'
  ) THEN
    EXECUTE 'DROP INDEX IF EXISTS public.ux_authors_email_lower';
  END IF;

  -- remover QUALQUER índice único redundante baseado em lower(email) que não seja o canônico
  FOR v_old_idx IN
    SELECT indexname
      FROM pg_indexes
     WHERE schemaname='public'
       AND tablename='authors'
       AND indexname <> 'uq_authors_email_lower'
  LOOP
    -- checa definição do índice
    IF EXISTS (
      SELECT 1
        FROM pg_indexes
       WHERE schemaname='public'
         AND indexname = v_old_idx
         AND indexdef ILIKE '%ON public.authors USING btree (lower(email))%'
    ) THEN
      EXECUTE format('DROP INDEX IF EXISTS public.%I', v_old_idx);
    END IF;
  END LOOP;
END$$;

-- 5) Higiene final (opcional): validar inexistência de duplicatas após índice
--    (se houver concorrência externa escrevendo, o índice UNIQUE garantirá a integridade)
--    Aqui apenas um sanity check via NOTICE.
DO $$
DECLARE
  v_cnt bigint;
BEGIN
  SELECT COUNT(*) INTO v_cnt
  FROM (
    SELECT lower(email) AS k, COUNT(*) c
    FROM public.authors
    GROUP BY 1
    HAVING COUNT(*) > 1
  ) d;
  IF v_cnt > 0 THEN
    RAISE NOTICE 'Ainda existem % duplicatas por lower(email); verifique gravações concorrentes.', v_cnt;
  END IF;
END$$;
