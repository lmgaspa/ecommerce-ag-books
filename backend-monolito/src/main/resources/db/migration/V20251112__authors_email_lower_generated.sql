-- Remover índice atual baseado em expressão
DROP INDEX IF EXISTS uq_authors_email_lower;

-- Substituir a coluna por uma gerada (não há ALTER direto para GENERATED)
ALTER TABLE public.authors
  DROP COLUMN IF EXISTS email_lower;

ALTER TABLE public.authors
  ADD COLUMN email_lower text GENERATED ALWAYS AS (lower(email)) STORED;

-- Índice único direto na coluna gerada
CREATE UNIQUE INDEX uq_authors_email_lower
  ON public.authors (email_lower);

-- Sanidade extra: garantir created_at ok (idempotente)
-- (opcional caso já esteja certo)
ALTER TABLE public.authors
  ALTER COLUMN created_at SET NOT NULL,
  ALTER COLUMN created_at SET DEFAULT now();
