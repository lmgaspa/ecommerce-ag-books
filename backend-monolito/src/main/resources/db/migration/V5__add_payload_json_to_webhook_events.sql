-- V5__add_payload_json_to_webhook_events.sql
-- Adiciona a coluna esperada pelo JPA e migra dados se houver 'payload' legado.

-- 1) Cria a coluna payload_json (jsonb)
ALTER TABLE payment_webhook_events
  ADD COLUMN IF NOT EXISTS payload_json JSONB NOT NULL DEFAULT '{}'::jsonb;

-- 2) Migra conteúdo legado, se existir coluna 'payload' (texto/json)
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name = 'payment_webhook_events' AND column_name = 'payload'
  ) THEN
    BEGIN
      EXECUTE $upd$
        UPDATE payment_webhook_events
           SET payload_json = payload::jsonb
         WHERE payload IS NOT NULL
           AND payload <> ''
      $upd$;
    EXCEPTION WHEN others THEN
      RAISE NOTICE 'Alguns registros não eram JSON válido; mantidos como {}';
    END;
    -- opcional: drop da coluna antiga
    -- EXECUTE 'ALTER TABLE payment_webhook_events DROP COLUMN payload';
  END IF;
END $$;

-- 3) Remover default (opcional; mantém a coluna NOT NULL)
ALTER TABLE payment_webhook_events
  ALTER COLUMN payload_json DROP DEFAULT;

-- 4) Garante o índice único de idempotência (caso não exista)
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_indexes
    WHERE schemaname = 'public' AND indexname = 'uq_webhook_event_idem'
  ) THEN
    EXECUTE 'CREATE UNIQUE INDEX uq_webhook_event_idem
               ON payment_webhook_events (provider, external_id, event_type)';
  END IF;
END $$;
