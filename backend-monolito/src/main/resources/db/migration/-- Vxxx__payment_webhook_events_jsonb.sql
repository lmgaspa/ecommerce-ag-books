-- Vxxx__payment_webhook_events_jsonb.sql
ALTER TABLE payment_webhook_events
    ADD COLUMN IF NOT EXISTS payload_json jsonb,
    ADD COLUMN IF NOT EXISTS received_at timestamptz NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS order_ref varchar(64);

-- Se a coluna legacy 'payload' existir e você quiser manter:
-- copie dados para payload_json quando estiver nulo
UPDATE payment_webhook_events
   SET payload_json = COALESCE(payload_json, payload)
 WHERE payload IS NOT NULL AND (payload_json IS NULL OR payload_json::text = 'null');

-- Opcional: se quiser remover 'payload' no futuro, só faça depois de migrar tudo:
-- ALTER TABLE payment_webhook_events DROP COLUMN payload;
