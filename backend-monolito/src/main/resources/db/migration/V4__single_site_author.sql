-- V1__payments_all.sql
-- Consolida:
-- 1) payment_site_author (singleton ativo) + auditoria/trigger + seed seguro (inativo)
-- 2) payment_webhook_events (idempotência)
-- 3) payment_payouts (garantias de 1:1 order_id, colunas mínimas, defaults, checks)

-- ============================================================================
-- 1) Autor único do site
-- ============================================================================

CREATE TABLE IF NOT EXISTS payment_site_author (
  id         BIGSERIAL PRIMARY KEY,
  name       VARCHAR(255) NOT NULL,
  email      VARCHAR(255),
  pix_key    VARCHAR(255) NOT NULL,
  active     BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Garante que só haja 1 autor ativo (índice parcial)
CREATE UNIQUE INDEX IF NOT EXISTS uq_site_author_single_active
  ON payment_site_author(active)
  WHERE active = TRUE;

-- Função de auditoria updated_at (cria apenas se não existir)
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_proc WHERE proname = 'tg_set_updated_at') THEN
    CREATE OR REPLACE FUNCTION tg_set_updated_at() RETURNS trigger AS $f$
    BEGIN
      NEW.updated_at := NOW();
      RETURN NEW;
    END;
    $f$ LANGUAGE plpgsql;
  END IF;
END $$;

-- Trigger de auditoria para payment_site_author
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'trg_payment_site_author_updated_at') THEN
    CREATE TRIGGER trg_payment_site_author_updated_at
      BEFORE UPDATE ON payment_site_author
      FOR EACH ROW EXECUTE FUNCTION tg_set_updated_at();
  END IF;
END $$;

-- Seed seguro: cria um registro INATIVO se a tabela estiver vazia
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM payment_site_author) THEN
    INSERT INTO payment_site_author(name, email, pix_key, active)
    VALUES ('Autor do Site', NULL, 'PREENCHA-SUA-CHAVE-PIX-AQUI', FALSE);
  END IF;
END $$;

-- ============================================================================
-- 2) Idempotência de webhook
-- ============================================================================

CREATE TABLE IF NOT EXISTS payment_webhook_events (
  id           BIGSERIAL PRIMARY KEY,
  provider     VARCHAR(60)  NOT NULL,   -- ex.: 'EFI_PIX'
  external_id  VARCHAR(200) NOT NULL,   -- txid/charge_id do provedor
  event_type   VARCHAR(120) NOT NULL,   -- ex.: 'PIX_CONFIRMED'
  payload_json JSONB        NOT NULL,
  received_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_webhook_event_idem
  ON payment_webhook_events(provider, external_id, event_type);

-- ============================================================================
-- 3) Payout por pedido (1:1) + colunas mínimas e constraints
-- ============================================================================

-- Caso ainda não exista a tabela, cria esqueleto básico
CREATE TABLE IF NOT EXISTS payment_payouts (
  id            BIGSERIAL PRIMARY KEY,
  order_id      BIGINT NOT NULL,
  status        VARCHAR(30)  NOT NULL DEFAULT 'CREATED',
  amount_gross  NUMERIC(12,2) NOT NULL DEFAULT 0,
  fee_percent   NUMERIC(6,3)  NOT NULL DEFAULT 0,
  fee_fixed     NUMERIC(12,2) NOT NULL DEFAULT 0,
  margin_percent NUMERIC(6,3) NOT NULL DEFAULT 0,
  margin_fixed  NUMERIC(12,2) NOT NULL DEFAULT 0,
  include_gateway_fees BOOLEAN NOT NULL DEFAULT FALSE,
  amount_net    NUMERIC(12,2) NOT NULL DEFAULT 0,
  min_send      NUMERIC(12,2) NOT NULL DEFAULT 0,
  fail_reason   TEXT,
  pix_key       VARCHAR(255),
  provider_ref  VARCHAR(200),
  created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  sent_at       TIMESTAMPTZ,
  confirmed_at  TIMESTAMPTZ,
  failed_at     TIMESTAMPTZ
);

-- Se houver uma constraint/índice antigo com (order_id, author_id), remove-o
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM pg_indexes
    WHERE schemaname = 'public' AND indexname = 'uq_payout_order_author'
  ) THEN
    EXECUTE 'DROP INDEX uq_payout_order_author';
  END IF;
END $$;

-- Remove coluna author_id se existir (modelo 1 autor no site)
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name = 'payment_payouts' AND column_name = 'author_id'
  ) THEN
    EXECUTE 'ALTER TABLE payment_payouts DROP COLUMN author_id';
  END IF;
END $$;

-- Garante UNIQUE(order_id) para 1 payout por pedido
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_indexes
    WHERE schemaname = 'public' AND indexname = 'uq_payout_order'
  ) THEN
    EXECUTE 'CREATE UNIQUE INDEX uq_payout_order ON payment_payouts(order_id)';
  END IF;
END $$;

-- Garante colunas mínimas e defaults (idempotente)
ALTER TABLE payment_payouts
  ALTER COLUMN created_at SET DEFAULT NOW();

ALTER TABLE payment_payouts
  ADD COLUMN IF NOT EXISTS status             VARCHAR(30)   NOT NULL DEFAULT 'CREATED',
  ADD COLUMN IF NOT EXISTS amount_gross       NUMERIC(12,2) NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS fee_percent        NUMERIC(6,3)  NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS fee_fixed          NUMERIC(12,2) NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS margin_percent     NUMERIC(6,3)  NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS margin_fixed       NUMERIC(12,2) NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS include_gateway_fees BOOLEAN     NOT NULL DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS amount_net         NUMERIC(12,2) NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS min_send           NUMERIC(12,2) NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS fail_reason        TEXT,
  ADD COLUMN IF NOT EXISTS pix_key            VARCHAR(255),
  ADD COLUMN IF NOT EXISTS provider_ref       VARCHAR(200),
  ADD COLUMN IF NOT EXISTS sent_at            TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS confirmed_at       TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS failed_at          TIMESTAMPTZ;

-- Evita payout em status "SENT/CONFIRMED" sem pix_key preenchida
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'chk_payout_pix_key_present'
  ) THEN
    ALTER TABLE payment_payouts
      ADD CONSTRAINT chk_payout_pix_key_present
      CHECK (
        (status IN ('CREATED','FAILED'))
        OR (pix_key IS NOT NULL AND length(trim(pix_key)) > 0)
      );
  END IF;
END $$;

-- ============================================================================
-- 4) Limpezas de tabelas antigas (opcional)
--    Evite CASCADE se não souber as dependências. Descomente se tiver certeza.
-- ============================================================================

-- DROP TABLE IF EXISTS payment_book_authors;
-- DROP TABLE IF EXISTS payment_author_accounts;
-- DROP TABLE IF EXISTS payment_author_registry;

-- ============================================================================
-- Fim
-- ============================================================================
