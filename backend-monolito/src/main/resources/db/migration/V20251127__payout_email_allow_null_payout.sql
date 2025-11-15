-- V20251127__payout_email_allow_null_payout.sql
-- Permite persistir e-mails de repasse mesmo sem payout_id (para segurança e auditoria)
-- Adiciona order_id para referência direta ao pedido

-- 1. Adicionar coluna order_id (se não existir) como NULL primeiro
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'payout_email'
      AND column_name = 'order_id'
  ) THEN
    ALTER TABLE payout_email
      ADD COLUMN order_id BIGINT;
  END IF;
END$$;

-- 2. Remover constraint NOT NULL de payout_id (tornar nullable)
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'payout_email'
      AND column_name = 'payout_id'
      AND is_nullable = 'NO'
  ) THEN
    ALTER TABLE payout_email
      ALTER COLUMN payout_id DROP NOT NULL;
  END IF;
END$$;

-- 3. Remover FK antiga (se existir) para permitir NULL
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conname = 'fk_payout_email_payout_id'
  ) THEN
    ALTER TABLE payout_email
      DROP CONSTRAINT fk_payout_email_payout_id;
  END IF;
END$$;

-- 4. Recriar FK de payout_id com ON DELETE SET NULL (permite NULL)
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conname = 'fk_payout_email_payout_id'
  ) THEN
    ALTER TABLE payout_email
      ADD CONSTRAINT fk_payout_email_payout_id
      FOREIGN KEY (payout_id)
      REFERENCES payment_payouts(id)
      ON DELETE SET NULL;
  END IF;
END$$;

-- 5. Adicionar FK para order_id (referência direta ao pedido)
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conname = 'fk_payout_email_order_id'
  ) THEN
    ALTER TABLE payout_email
      ADD CONSTRAINT fk_payout_email_order_id
      FOREIGN KEY (order_id)
      REFERENCES orders(id)
      ON DELETE CASCADE;
  END IF;
END$$;

-- 6. Criar índice em order_id (para consultas eficientes)
CREATE INDEX IF NOT EXISTS idx_payout_email_order_id ON payout_email(order_id);

-- 7. Atualizar registros existentes com order_id (se possível)
-- Busca order_id a partir de payout_id para registros existentes
UPDATE payout_email pe
SET order_id = pp.order_id
FROM payment_payouts pp
WHERE pe.payout_id = pp.id
  AND pe.order_id IS NULL;

-- 8. Nota: order_id é nullable no banco para compatibilidade com registros antigos,
--    mas o código Kotlin sempre preenche order_id em novos registros.
--    Isso garante auditoria completa mesmo sem payout_id.

