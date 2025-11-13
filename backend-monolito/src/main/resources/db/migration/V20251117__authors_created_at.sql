-- Garante a coluna created_at na tabela authors (se não existir)
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name   = 'authors'
      AND column_name  = 'created_at'
  ) THEN
    ALTER TABLE public.authors
      ADD COLUMN created_at timestamptz NOT NULL DEFAULT now();
  END IF;
END $$;

-- Segurança extra: se por algum motivo houver NULLs, preenche
UPDATE public.authors
SET created_at = now()
WHERE created_at IS NULL;

-- Índice útil por data de criação (opcional, idempotente)
CREATE INDEX IF NOT EXISTS ix_authors_created_at
  ON public.authors (created_at DESC);
