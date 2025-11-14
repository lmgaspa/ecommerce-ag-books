-- V20251122__normalize_books_author_fk.sql
-- Normaliza FK de autor em books e recria views de suporte, evitando renomear colunas de VIEW.

-- 0) Garantias básicas
-- 0.1) author_id em books (se não existir)
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema='public' AND table_name='books' AND column_name='author_id'
  ) THEN
    ALTER TABLE books ADD COLUMN author_id BIGINT;
  END IF;
END$$;

-- 0.2) FK (se não existir)
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conname = 'fk_books_author'
  ) THEN
    ALTER TABLE books
      ADD CONSTRAINT fk_books_author
      FOREIGN KEY (author_id) REFERENCES authors(id) ON UPDATE CASCADE ON DELETE SET NULL;
  END IF;
END$$;

-- 0.3) Índice para consultas por autor
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_indexes WHERE schemaname='public' AND indexname='idx_books_author_id'
  ) THEN
    CREATE INDEX idx_books_author_id ON books(author_id);
  END IF;
END$$;

-- 1) Views — derruba e recria (evita erro "cannot change name of view column ...")
DROP VIEW IF EXISTS vw_books_without_author;
DROP VIEW IF EXISTS vw_books_authors_cardinality;

-- 1.1) Livros sem autor
CREATE VIEW vw_books_without_author AS
SELECT
  b.id    AS book_id,
  b.title AS book_title
FROM books b
WHERE b.author_id IS NULL;

-- 1.2) Cardinalidade: 0 (sem autor), 1 (tem autor)
CREATE VIEW vw_books_authors_cardinality AS
SELECT
  b.id    AS book_id,
  b.title AS book_title,
  CASE WHEN b.author_id IS NULL THEN 0 ELSE 1 END AS authors_count
FROM books b;

-- 2) (Opcional) Mapear livros sem autor para o "site author" se desejado aqui (normalmente deixamos para a V20251123)
-- -- Exemplo:
-- WITH site_author AS (
--   SELECT a.id AS author_id
--   FROM authors a
--   JOIN payment_site_author ps ON ps.author_id = a.id
--   LIMIT 1
-- )
-- UPDATE books b
-- SET author_id = (SELECT author_id FROM site_author)
-- WHERE b.author_id IS NULL;
