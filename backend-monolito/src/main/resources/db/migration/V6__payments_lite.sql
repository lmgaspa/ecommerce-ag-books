-- V6__payments_lite.sql
-- Tabelas mínimas p/ webhook + payout 1:1 por pedido

CREATE TABLE IF NOT EXISTS payment_site_author (
  id         BIGSERIAL PRIMARY KEY,
  name       VARCHAR(255) NOT NULL,
  email      VARCHAR(255),
  pix_key    VARCHAR(255) NOT NULL,
  active     BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_site_author_single_active
  ON payment_site_author(active) WHERE active = TRUE;

CREATE TABLE IF NOT EXISTS payment_webhook_events (
  id           BIGSERIAL PRIMARY KEY,
  provider     VARCHAR(60)  NOT NULL,
  external_id  VARCHAR(200) NOT NULL,
  event_type   VARCHAR(120) NOT NULL,
  payload_json JSONB        NOT NULL,
  received_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_webhook_event_idem
  ON payment_webhook_events (provider, external_id, event_type);

CREATE TABLE IF NOT EXISTS payment_payouts (
  id              BIGSERIAL PRIMARY KEY,
  order_id        BIGINT NOT NULL,
  status          VARCHAR(30) NOT NULL,  -- CREATED, SENT, CONFIRMED, FAILED
  amount_gross    NUMERIC(12,2) NOT NULL,
  amount_net      NUMERIC(12,2) NOT NULL DEFAULT 0,
  fee_percent     NUMERIC(6,3)  NOT NULL DEFAULT 0,
  fee_fixed       NUMERIC(12,2) NOT NULL DEFAULT 0,
  margin_percent  NUMERIC(6,3)  NOT NULL DEFAULT 0,
  margin_fixed    NUMERIC(12,2) NOT NULL DEFAULT 0,
  include_gateway_fees BOOLEAN NOT NULL DEFAULT FALSE,
  min_send        NUMERIC(12,2) NOT NULL DEFAULT 0,
  fail_reason     TEXT,
  pix_key         VARCHAR(255),
  provider_ref    VARCHAR(200),
  created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  sent_at         TIMESTAMPTZ,
  confirmed_at    TIMESTAMPTZ,
  failed_at       TIMESTAMPTZ
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_payout_order ON payment_payouts(order_id);
