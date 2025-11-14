-- V20251123__site_author_fk_and_sync.sql
-- 1) Garante unicidade de e-mail em authors (lower(email))
-- 2) Upsert do autor oficial (SITE_AUTHOR_NAME/EMAIL)
-- 3) Sincroniza payment_site_author (apenas 1 ativo)
-- 4) Mapeia livros órfãos -> author_id do autor oficial
-- 5) (Re)cria views de conferência

DO $$
DECLARE
  v_name   text := '${SITE_AUTHOR_NAME}';
  v_email  text := '${SITE_AUTHOR_EMAIL}';
  v_pix    text := '${SITE_AUTHOR_PIX_KEY}';
  v_author_id bigint;
  v_has_uniq boolean;
  v_has_psa_email_uniq boolean;
BEGIN
  --------------------------------------------------------------------
  -- 0) Garante índice único por lower(email) em authors
  --------------------------------------------------------------------
  SELECT EXISTS (
    SELECT 1
    FROM pg_indexes i
    WHERE i.tablename = 'authors'
      AND i.indexdef ILIKE 'CREATE UNIQUE INDEX%ON authors (lower((email%))%'
  ) INTO v_has_uniq;

  IF NOT v_has_uniq THEN
    EXECUTE 'CREATE UNIQUE INDEX IF NOT EXISTS uq_authors_email_lower ON authors (lower(email))';
  END IF;

  --------------------------------------------------------------------
  -- 1) UPSERT do autor oficial em authors
  --    Tenta usar a constraint do índice acima; se faltar, cai em fallback.
  --------------------------------------------------------------------
  BEGIN
    INSERT INTO authors (name, email)
    VALUES (v_name, v_email)
    ON CONFLICT ON CONSTRAINT uq_authors_email_lower
      DO UPDATE SET name = EXCLUDED.name
    RETURNING id INTO v_author_id;
  EXCEPTION WHEN undefined_object THEN
    -- Fallback robusto caso o nome da constraint/índice mude
    PERFORM 1 FROM authors WHERE lower(email)=lower(v_email);
    IF FOUND THEN
      UPDATE authors SET name = v_name WHERE lower(email)=lower(v_email);
      SELECT id INTO v_author_id FROM authors WHERE lower(email)=lower(v_email) LIMIT 1;
    ELSE
      INSERT INTO authors (name, email) VALUES (v_name, v_email)
      RETURNING id INTO v_author_id;
    END IF;
  END;

  --------------------------------------------------------------------
  -- 2) payment_site_author: garantir unicidade em email e sincronizar
  --------------------------------------------------------------------
  SELECT EXISTS (
    SELECT 1
    FROM pg_indexes i
    WHERE i.tablename = 'payment_site_author'
      AND i.indexdef ILIKE 'CREATE UNIQUE INDEX%ON payment_site_author (lower((email%))%'
  ) INTO v_has_psa_email_uniq;

  IF NOT v_has_psa_email_uniq THEN
    EXECUTE 'CREATE UNIQUE INDEX IF NOT EXISTS ux_site_author_email ON payment_site_author (lower(email))';
  END IF;

  -- Upsert do registro “oficial” (apenas um ativo; desativa os demais)
  BEGIN
    INSERT INTO payment_site_author (name, email, pix_key, active)
    VALUES (v_name, v_email, v_pix, TRUE)
    ON CONFLICT ON CONSTRAINT ux_site_author_email
      DO UPDATE SET name    = EXCLUDED.name,
                    pix_key = EXCLUDED.pix_key,
                    active  = TRUE;
  EXCEPTION WHEN undefined_object THEN
    -- Fallback por coluna
    INSERT INTO payment_site_author (name, email, pix_key, active)
    VALUES (v_name, v_email, v_pix, TRUE)
    ON CONFLICT (email)
      DO UPDATE SET name    = EXCLUDED.name,
                    pix_key = EXCLUDED.pix_key,
                    active  = TRUE;
  END;

  -- Garante single-active
  UPDATE payment_site_author
     SET active = CASE WHEN lower(email)=lower(v_email) THEN TRUE ELSE FALSE END;

  --------------------------------------------------------------------
  -- 3) Mapeia livros órfãos para o autor do site (assumindo books.author_id já existe)
  --------------------------------------------------------------------
  PERFORM 1
    FROM information_schema.columns
   WHERE table_schema='public' AND table_name='books' AND column_name='author_id';
  IF FOUND THEN
    UPDATE books
       SET author_id = v_author_id
     WHERE author_id IS NULL;
    -- Índice de FK para leitura
    CREATE INDEX IF NOT EXISTS idx_books_author_id ON books(author_id);
  END IF;

END $$;

----------------------------------------------------------------------
-- 4) Views de conferência (idempotentes)
----------------------------------------------------------------------
CREATE OR REPLACE VIEW vw_books_without_author AS
SELECT b.id AS book_id, b.title AS book_title
FROM books b
LEFT JOIN LATERAL (
  SELECT TRUE AS has_author
  FROM information_schema.columns
  WHERE table_schema='public' AND table_name='books' AND column_name='author_id'
) c ON TRUE
WHERE c.has_author IS TRUE
  AND b.author_id IS NULL;

CREATE OR REPLACE VIEW vw_books_authors_cardinality AS
SELECT b.id AS book_id,
       b.title AS book_title,
       CASE WHEN b.author_id IS NULL THEN 0 ELSE 1 END AS authors_count
FROM books b;
