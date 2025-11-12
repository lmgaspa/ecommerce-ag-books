-- db/migration/V20251116__orders_core_indexes.sql

-- Garante a coluna created_at na tabela orders (se não existir)
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name   = 'orders'
      AND column_name  = 'created_at'
  ) THEN
    ALTER TABLE public.orders
      ADD COLUMN created_at timestamptz NOT NULL DEFAULT now();
  END IF;
END $$;

-- Caso você já tenha dados antigos e queira preencher created_at onde ainda é NULL (segurança extra)
UPDATE public.orders
SET created_at = now()
WHERE created_at IS NULL;

-- Índice por data de criação (desc) — idempotente
CREATE INDEX IF NOT EXISTS ix_orders_created_at
  ON public.orders (created_at DESC);
