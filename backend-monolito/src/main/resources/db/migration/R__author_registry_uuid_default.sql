-- Extensão segura/idem-potente
CREATE EXTENSION IF NOT EXISTS pgcrypto;


-- Garante tipo + default de UUID na coluna
ALTER TABLE payment_author_registry
ALTER COLUMN author_uuid DROP DEFAULT,
ALTER COLUMN author_uuid TYPE uuid USING author_uuid::uuid,
ALTER COLUMN author_uuid SET DEFAULT gen_random_uuid();


-- Backfill para linhas antigas sem UUID
UPDATE payment_author_registry
SET author_uuid = gen_random_uuid()
WHERE author_uuid IS NULL;


-- Reforça NOT NULL
ALTER TABLE payment_author_registry
ALTER COLUMN author_uuid SET NOT NULL;


-- Índice único por e-mail (case-insensitive) — evita duplicatas no bootstrap
DO $$
BEGIN
IF NOT EXISTS (
SELECT 1 FROM pg_indexes WHERE indexname = 'ux_author_registry_email'
) THEN
CREATE UNIQUE INDEX ux_author_registry_email
ON payment_author_registry (lower(email));
END IF;
END$$;


-- Índice único por UUID (defensivo)
DO $$
BEGIN
IF NOT EXISTS (
SELECT 1 FROM pg_indexes WHERE indexname = 'ux_author_registry_uuid'
) THEN
CREATE UNIQUE INDEX ux_author_registry_uuid
ON payment_author_registry (author_uuid);
END IF;
END$$;