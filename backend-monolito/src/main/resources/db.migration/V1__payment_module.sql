-- PostgreSQL

-- 1) eventos brutos de webhook (idempotência e auditoria)
CREATE TABLE payment_webhook_events (
  id UUID PRIMARY KEY,
  provider VARCHAR(20) NOT NULL,          -- 'PIX' | 'CARD'
  event_type VARCHAR(60) NOT NULL,        -- ex.: 'pix.paid', 'card.captured'
  external_id VARCHAR(80) NOT NULL,       -- txid (Pix) ou chargeId (Cartão)
  order_ref VARCHAR(80) NULL,             -- referência ao pedido (metadata do provedor)
  payload JSONB NOT NULL,
  received_at TIMESTAMP NOT NULL DEFAULT now(),
  UNIQUE (provider, external_id, event_type)
);

-- 2) chave Pix do autor (se você não tiver em tabela existente)
CREATE TABLE payment_author_accounts (
  author_id UUID PRIMARY KEY,
  pix_key   VARCHAR(140) NOT NULL
);

-- 3) repasses gerados por este módulo
CREATE TABLE payment_payouts (
  id UUID PRIMARY KEY,
  author_id UUID NOT NULL,
  order_id  UUID NOT NULL,
  amount NUMERIC(12,2) NOT NULL,
  status VARCHAR(20) NOT NULL,            -- CREATED | SENT | CONFIRMED | FAILED | CANCELED
  pix_key VARCHAR(140) NOT NULL,
  efi_id_envio VARCHAR(64) UNIQUE,
  fail_reason TEXT,
  created_at TIMESTAMP NOT NULL DEFAULT now(),
  sent_at TIMESTAMP NULL,
  confirmed_at TIMESTAMP NULL
);

CREATE INDEX idx_payment_payouts_order  ON payment_payouts(order_id);
CREATE INDEX idx_payment_payouts_author ON payment_payouts(author_id);
