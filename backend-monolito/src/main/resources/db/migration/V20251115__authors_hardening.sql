-- 1) Padroniza entradas novas (trim) e evita nulos em email
ALTER TABLE authors
  ALTER COLUMN email TYPE text,
  ALTER COLUMN email SET NOT NULL;

-- 2) Garante formato básico de e-mail (simples e suficiente)
ALTER TABLE authors
  ADD CONSTRAINT chk_authors_email_format
  CHECK (email ~* '^[^@\s]+@[^@\s]+\.[^@\s]+$');

-- 3) Normaliza espaço/brancos de forma idempotente (UPDATE único)
UPDATE authors SET email = trim(email);

-- 4) (Opcional) Gera coluna derivada para consultas/depuração
--    Não troca sua regra; só expõe o normalizado
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name='authors' AND column_name='email_lower'
  ) THEN
    ALTER TABLE authors
      ADD COLUMN email_lower text GENERATED ALWAYS AS (lower(email)) STORED;
  END IF;
END$$;

-- 5) Índice já existe, só garante nome e alvo corretos
-- DROP INDEX IF EXISTS ux_authors_email_lower;
-- CREATE UNIQUE INDEX ux_authors_email_lower ON authors (lower(email));
