-- V20251121__set_site_author_from_placeholders_and_map_all_books.sql
-- Objetivo (idempotente, versão única):
-- - Garantir o autor "${SITE_AUTHOR_NAME} <${SITE_AUTHOR_EMAIL}>" na registry (com UUID)
-- - Torná-lo o site author ativo, com PIX ${SITE_AUTHOR_PIX_KEY}
-- - (Re)mapear TODOS os livros (books) para esse autor
-- - Recriar views canônicas + views auxiliares (v2) para dashboards/diagnóstico
-- - Tolerante à reexecução e ausência de tabelas opcionais

/* ===================== 0) Extensão ===================== */
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pgcrypto') THEN
    CREATE EXTENSION IF NOT EXISTS pgcrypto;
  END IF;
END$$;

/* = 0.1) Índices únicos necessários para ON CONFLICT = */
DO $$
BEGIN
  -- payment_author_registry.email único
  IF NOT EXISTS (
    SELECT 1 FROM pg_indexes
    WHERE schemaname='public' AND indexname='ux_author_registry_email'
  ) THEN
    CREATE UNIQUE INDEX ux_author_registry_email
      ON public.payment_author_registry(email);
  END IF;

  -- payment_site_author.email único
  IF NOT EXISTS (
    SELECT 1 FROM pg_indexes
    WHERE schemaname='public' AND indexname='ux_site_author_email'
  ) THEN
    CREATE UNIQUE INDEX ux_site_author_email
      ON public.payment_site_author(email);
  END IF;
END$$;

/* = 1) Upsert do autor principal na registry (mantém UUID existente) = */
WITH ins AS (
  INSERT INTO public.payment_author_registry (name, email, author_uuid, created_at, updated_at)
  VALUES ('${SITE_AUTHOR_NAME}', '${SITE_AUTHOR_EMAIL}', gen_random_uuid(), NOW(), NOW())
  ON CONFLICT (email) DO UPDATE
    SET name       = EXCLUDED.name,
        updated_at = NOW()
  RETURNING id
)
UPDATE public.payment_author_registry par
   SET author_uuid = COALESCE(par.author_uuid, gen_random_uuid()),
       updated_at  = NOW()
 WHERE lower(par.email) = lower('${SITE_AUTHOR_EMAIL}')
   AND par.author_uuid IS NULL;

/* = 2) Promover como site author ativo com PIX, desativando os demais = */
DO $$
BEGIN
  UPDATE public.payment_site_author SET active = FALSE WHERE active = TRUE;

  INSERT INTO public.payment_site_author (name, email, pix_key, active, created_at, updated_at)
  VALUES ('${SITE_AUTHOR_NAME}', '${SITE_AUTHOR_EMAIL}', '${SITE_AUTHOR_PIX_KEY}', TRUE, NOW(), NOW())
  ON CONFLICT (email) DO UPDATE
    SET name       = EXCLUDED.name,
        pix_key    = EXCLUDED.pix_key,
        active     = TRUE,
        updated_at = NOW();
END$$;

/* = 3) Mapear TODOS os livros para o author_id do site author ativo = */
DO $$
DECLARE
  v_author_id BIGINT;
  v_books_exists BOOLEAN := FALSE;
BEGIN
  SELECT par.id
    INTO v_author_id
  FROM public.payment_site_author psa
  JOIN public.payment_author_registry par
    ON lower(par.email) = lower(psa.email)
  WHERE psa.active = TRUE
  LIMIT 1;

  IF v_author_id IS NULL THEN
    RAISE NOTICE 'Nenhum site author ativo resolvido na registry. Nada a mapear.';
    RETURN;
  END IF;

  -- verifica existência de books
  PERFORM 1 FROM pg_class c
   JOIN pg_namespace n ON n.oid = c.relnamespace
  WHERE n.nspname = 'public' AND c.relname = 'books' AND c.relkind = 'r';
  IF FOUND THEN
    v_books_exists := TRUE;
  END IF;

  IF NOT v_books_exists THEN
    RAISE NOTICE 'Tabela public.books não existe. Nada a mapear.';
    RETURN;
  END IF;

  -- garante PK/índice único em (book_id) para suportar ON CONFLICT
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'payment_book_authors_pkey'
  ) THEN
    BEGIN
      ALTER TABLE public.payment_book_authors
        ADD CONSTRAINT payment_book_authors_pkey PRIMARY KEY (book_id);
    EXCEPTION WHEN others THEN
      NULL;
    END;
  END IF;

  INSERT INTO public.payment_book_authors (book_id, author_id)
  SELECT b.id, v_author_id
    FROM public.books b
  ON CONFLICT (book_id) DO UPDATE
    SET author_id = EXCLUDED.author_id
  WHERE public.payment_book_authors.author_id IS DISTINCT FROM EXCLUDED.author_id;
END$$;

/* = 4) Views canônicas (DROP + CREATE) = */
DROP VIEW IF EXISTS public.vw_site_author;
CREATE VIEW public.vw_site_author AS
SELECT
  psa.id         AS site_author_id,
  psa.name       AS site_author_name,
  psa.email      AS site_author_email,
  psa.pix_key    AS site_author_pix_key,
  psa.active     AS site_author_active,
  par.id         AS author_id,
  par.author_uuid
FROM public.payment_site_author psa
LEFT JOIN public.payment_author_registry par
  ON lower(par.email) = lower(psa.email)
WHERE psa.active = TRUE;

DROP VIEW IF EXISTS public.vw_books_with_author;
CREATE VIEW public.vw_books_with_author AS
SELECT
  b.id           AS book_id,
  b.title        AS book_title,
  par.id         AS author_id,
  par.name       AS author_name,
  par.email      AS author_email
FROM public.books b
JOIN public.payment_book_authors pba ON pba.book_id = b.id
JOIN public.payment_author_registry par ON par.id = pba.author_id;

/* = 5) Views auxiliares “v2” para diagnóstico e dashboard = */

-- 5.1 Livros sem mapeamento (órfãos)
DROP VIEW IF EXISTS public.vw_books_without_author;
CREATE VIEW public.vw_books_without_author AS
SELECT b.id AS book_id, b.title AS book_title
FROM public.books b
LEFT JOIN public.payment_book_authors pba ON pba.book_id = b.id
WHERE pba.book_id IS NULL
ORDER BY b.id;

-- 5.2 Inventário do autor do site: total de livros, últimos títulos
DROP VIEW IF EXISTS public.vw_site_author_inventory;
CREATE VIEW public.vw_site_author_inventory AS
WITH site AS (
  SELECT par.id AS author_id
  FROM public.payment_site_author psa
  JOIN public.payment_author_registry par
    ON lower(par.email) = lower(psa.email)
  WHERE psa.active = TRUE
  LIMIT 1
),
books_of AS (
  SELECT b.id, b.title
  FROM site s
  JOIN public.payment_book_authors pba ON pba.author_id = s.author_id
  JOIN public.books b ON b.id = pba.book_id
)
SELECT
  (SELECT COUNT(*) FROM books_of) AS total_books,
  (SELECT string_agg(title, ' | ' ORDER BY id DESC) FROM (SELECT title, id FROM books_of ORDER BY id DESC LIMIT 5) t) AS latest_5_titles;

-- 5.3 Contagem de autores por livro (deve ser 1 em nosso modelo)
DROP VIEW IF EXISTS public.vw_books_authors_cardinality;
CREATE VIEW public.vw_books_authors_cardinality AS
SELECT
  pba.book_id,
  COUNT(*) AS authors_count
FROM public.payment_book_authors pba
GROUP BY pba.book_id
HAVING COUNT(*) > 1
ORDER BY authors_count DESC, pba.book_id;
