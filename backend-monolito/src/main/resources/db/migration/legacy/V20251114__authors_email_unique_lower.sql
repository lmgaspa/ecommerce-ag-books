-- 1) Garante a tabela authors (se não existir)
CREATE TABLE IF NOT EXISTS authors (
  id    BIGSERIAL PRIMARY KEY,
  name  VARCHAR(255) NOT NULL,
  email VARCHAR(255) NOT NULL
);

-- 2) (Opcional, mas prudente) Deduplica emails antes de impor unicidade case-insensitive
--    Mantém o menor id por email (case-insensitive) e remove os demais.
WITH dups AS (
  SELECT id
  FROM (
    SELECT
      id,
      ROW_NUMBER() OVER (PARTITION BY lower(email) ORDER BY id) AS rn
    FROM authors
  ) t
  WHERE t.rn > 1
)
DELETE FROM authors a
USING dups d
WHERE a.id = d.id;

-- 3) Cria o índice único em lower(email), se ainda não existir
CREATE UNIQUE INDEX IF NOT EXISTS ux_authors_email_lower
  ON authors (lower(email));
