-- V20251121__force_all_books_to_site_author.sql
-- Normaliza: força TODOS os livros a apontarem para o site author ativo
-- e cria views utilitárias p/ dashboard e export.

DO $$
DECLARE
  v_author_id BIGINT;
BEGIN
  -- 1) Resolve o author_id do site author ativo (via e-mail)
  SELECT par.id
    INTO v_author_id
  FROM payment_site_author psa
  JOIN payment_author_registry par
    ON lower(par.email) = lower(psa.email)
  WHERE psa.active = TRUE
  LIMIT 1;

  IF v_author_id IS NULL THEN
    RAISE NOTICE 'Nenhum site author ativo com e-mail resolvido na registry. Nada foi alterado.';
    RETURN;
  END IF;

  -- 2) Upsert: amarra todos os books → author_id do site author ativo
  INSERT INTO payment_book_authors (book_id, author_id)
  SELECT b.id, v_author_id
  FROM books b
  ON CONFLICT (book_id) DO UPDATE
    SET author_id = EXCLUDED.author_id
    WHERE payment_book_authors.author_id IS DISTINCT FROM EXCLUDED.author_id;
END $$;

-- 3) Views utilitárias (idempotentes)

-- 3.1 Autor ativo do site (inclui author_id e author_uuid p/ export)
CREATE OR REPLACE VIEW vw_site_author AS
SELECT
  psa.id       AS site_author_id,
  psa.name     AS site_author_name,
  psa.email    AS site_author_email,
  psa.active,
  par.id       AS author_id,
  par.author_uuid
FROM payment_site_author psa
LEFT JOIN payment_author_registry par
  ON lower(par.email) = lower(psa.email)
WHERE psa.active = TRUE;

-- 3.2 Livros com autor (para listagens no dashboard)
CREATE OR REPLACE VIEW vw_books_with_author AS
SELECT
  b.id     AS book_id,
  b.title  AS book_title,
  par.id   AS author_id,
  par.name AS author_name,
  par.email AS author_email
FROM books b
JOIN payment_book_authors pba ON pba.book_id = b.id
JOIN payment_author_registry par ON par.id = pba.author_id;

-- 3.3 Export direto do autor ativo (id + uuid + nome + e-mail)
CREATE OR REPLACE VIEW vw_site_author_export AS
SELECT
  par.id         AS author_id,
  par.author_uuid,
  par.name,
  par.email
FROM payment_site_author psa
JOIN payment_author_registry par
  ON lower(par.email) = lower(psa.email)
WHERE psa.active = TRUE
LIMIT 1;

-- =========================
-- EXEMPLOS DE USO (fora da migration):
--   -- Exportar o autor por e-mail específico:
--   SELECT id AS author_id, author_uuid, name, email
--   FROM payment_author_registry
--   WHERE lower(email) = 'ag1957@gmail.com';
--
--   -- Exportar o autor ATIVO do site (sem precisar do e-mail):
--   SELECT * FROM vw_site_author_export;
-- =========================
