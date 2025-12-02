-- V1__baseline_ecommerce.sql
-- Baseline idempotente do Ecommerce AG Books

-- =====================================================================
-- 0) Extensões e função utilitária
-- =====================================================================

-- Necessário para gen_random_uuid()
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Função genérica para updated_at
CREATE OR REPLACE FUNCTION tg_set_updated_at()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$;

-- =====================================================================
-- 1) AUTHORS e BOOKS
-- =====================================================================

CREATE TABLE IF NOT EXISTS authors (
  id          BIGSERIAL PRIMARY KEY,
  name        VARCHAR(255) NOT NULL,
  email       TEXT         NOT NULL,
  email_lower TEXT GENERATED ALWAYS AS (lower(email)) STORED,
  created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CONSTRAINT chk_authors_email_format
    CHECK (email ~* '^[^@\s]+@[^@\s]+\.[^@\s]+$')
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_authors_email_lower
  ON authors (lower(email));

CREATE INDEX IF NOT EXISTS ix_authors_created_at
  ON authors (created_at DESC);

CREATE TABLE IF NOT EXISTS books (
  id          VARCHAR(255) PRIMARY KEY,
  author      VARCHAR(255) NOT NULL,      -- texto legado
  category    VARCHAR(255) NOT NULL,
  description TEXT,
  image_url   VARCHAR(255) NOT NULL,
  price       DOUBLE PRECISION NOT NULL,  -- mapeado em Kotlin como Double
  stock       INTEGER NOT NULL,
  title       VARCHAR(255) NOT NULL,
  author_id   BIGINT,
  CONSTRAINT fk_books_author
    FOREIGN KEY (author_id)
      REFERENCES authors(id)
      ON UPDATE CASCADE
      ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_books_author_id ON books(author_id);
CREATE INDEX IF NOT EXISTS idx_books_stock      ON books(stock);

-- =====================================================================
-- 2) COUPONS e ORDER_COUPONS
-- =====================================================================

CREATE TABLE IF NOT EXISTS coupons (
  id                      BIGSERIAL PRIMARY KEY,
  code                    VARCHAR(50)  NOT NULL,
  name                    VARCHAR(200) NOT NULL,
  description             TEXT,
  discount_type           VARCHAR(20)  NOT NULL,
  discount_value          NUMERIC(38,2) NOT NULL,
  minimum_order_value     NUMERIC(38,2) NOT NULL DEFAULT 0,
  maximum_discount_value  NUMERIC(38,2),
  usage_limit             INTEGER,
  usage_limit_per_user    INTEGER,
  valid_from              TIMESTAMPTZ NOT NULL,
  valid_until             TIMESTAMPTZ,
  active                  BOOLEAN NOT NULL DEFAULT TRUE,
  created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT coupons_discount_type_check
    CHECK (discount_type IN ('FIXED','PERCENTAGE')),
  CONSTRAINT coupons_discount_value_check
    CHECK (discount_value >= 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS coupons_code_key ON coupons(code);
CREATE INDEX IF NOT EXISTS idx_coupons_code        ON coupons(code);
CREATE INDEX IF NOT EXISTS idx_coupons_active      ON coupons(active);
CREATE INDEX IF NOT EXISTS idx_coupons_valid_dates ON coupons(valid_from, valid_until);

-- Trigger de updated_at em coupons
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_trigger WHERE tgname = 'trg_coupons_updated_at'
  ) THEN
    CREATE TRIGGER trg_coupons_updated_at
    BEFORE UPDATE ON coupons
    FOR EACH ROW
    EXECUTE FUNCTION tg_set_updated_at();
  END IF;
END;
$$;

CREATE TABLE IF NOT EXISTS order_coupons (
  id              BIGSERIAL PRIMARY KEY,
  order_id        BIGINT NOT NULL,
  coupon_id       BIGINT NOT NULL,
  original_total  NUMERIC(38,2) NOT NULL,
  discount_amount NUMERIC(38,2) NOT NULL,
  final_total     NUMERIC(38,2) NOT NULL,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT order_coupons_order_id_coupon_id_key UNIQUE(order_id, coupon_id),
  CONSTRAINT order_coupons_order_id_fkey
    FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
  CONSTRAINT order_coupons_coupon_id_fkey
    FOREIGN KEY (coupon_id) REFERENCES coupons(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_order_coupons_order_id  ON order_coupons(order_id);
CREATE INDEX IF NOT EXISTS idx_order_coupons_coupon_id ON order_coupons(coupon_id);

-- =====================================================================
-- 3) ORDERS e ORDER_ITEMS
-- =====================================================================

CREATE TABLE IF NOT EXISTS orders (
  id                 BIGSERIAL PRIMARY KEY,

  -- Cliente
  first_name         VARCHAR(255) NOT NULL,
  last_name          VARCHAR(255) NOT NULL,
  email              VARCHAR(255) NOT NULL,
  cpf                VARCHAR(255) NOT NULL,
  number             VARCHAR(255) NOT NULL,
  complement         VARCHAR(255),
  district           VARCHAR(255) NOT NULL,

  -- Endereço
  address            VARCHAR(255) NOT NULL,
  city               VARCHAR(255) NOT NULL,
  state              VARCHAR(255) NOT NULL,
  cep                VARCHAR(255) NOT NULL,
  phone              VARCHAR(255) NOT NULL,

  note               TEXT,

  -- Cartão / parcelas
  installments       INTEGER,

  -- "card" ou "pix"
  payment_method     VARCHAR(8) NOT NULL,

  -- Charge da Efí
  charge_id          VARCHAR(255),

  -- Valores
  total              NUMERIC(10,2) NOT NULL,
  shipping           NUMERIC(10,2) NOT NULL,

  -- Cupom aplicado
  coupon_code        VARCHAR(50),
  discount_amount    NUMERIC(10,2),

  -- Pagamento
  paid               BOOLEAN NOT NULL DEFAULT FALSE,
  txid               VARCHAR(35),

  mailed_at          TIMESTAMPTZ,
  qr_code            TEXT,
  qr_code_base64     TEXT,

  -- Status interno (mapeado para OrderStatus enum)
  status             VARCHAR(32) NOT NULL DEFAULT 'NEW',

  reserve_expires_at TIMESTAMPTZ,
  paid_at            TIMESTAMPTZ,

  created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_orders_txid
  ON orders(txid) WHERE txid IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_orders_charge_id
  ON orders(charge_id) WHERE charge_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_orders_status             ON orders(status);
CREATE INDEX IF NOT EXISTS idx_orders_reserve_expires_at ON orders(reserve_expires_at);
CREATE INDEX IF NOT EXISTS idx_orders_coupon_code        ON orders(coupon_code);
CREATE INDEX IF NOT EXISTS ix_orders_created_at          ON orders(created_at DESC);

CREATE TABLE IF NOT EXISTS order_items (
  id        BIGSERIAL PRIMARY KEY,
  book_id   VARCHAR(36)  NOT NULL,
  price     NUMERIC(10,2) NOT NULL,
  quantity  INTEGER NOT NULL,
  title     VARCHAR(255) NOT NULL,
  order_id  BIGINT NOT NULL,
  image_url VARCHAR(255),
  CONSTRAINT fkbioxgbv59vetrxe0ejfubep1w
    FOREIGN KEY (order_id) REFERENCES orders(id)
);

-- =====================================================================
-- 4) PAYMENT AUTHOR REGISTRY / ACCOUNTS / SITE AUTHOR
-- =====================================================================

CREATE TABLE IF NOT EXISTS payment_author_registry (
  id          BIGSERIAL PRIMARY KEY,
  name        VARCHAR(255) NOT NULL,
  email       VARCHAR(255),
  author_uuid UUID NOT NULL DEFAULT gen_random_uuid(),
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_author_registry_email
  ON payment_author_registry(email);

CREATE UNIQUE INDEX IF NOT EXISTS ux_author_registry_uuid
  ON payment_author_registry(author_uuid);

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_trigger WHERE tgname = 'trg_payment_author_registry_updated_at'
  ) THEN
    CREATE TRIGGER trg_payment_author_registry_updated_at
    BEFORE UPDATE ON payment_author_registry
    FOR EACH ROW
    EXECUTE FUNCTION tg_set_updated_at();
  END IF;
END;
$$;

CREATE TABLE IF NOT EXISTS payment_author_accounts (
  author_id BIGINT PRIMARY KEY,
  pix_key   VARCHAR(255) NOT NULL,
  CONSTRAINT payment_author_accounts_author_id_fkey
    FOREIGN KEY (author_id) REFERENCES payment_author_registry(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS payment_site_author (
  id         BIGSERIAL PRIMARY KEY,
  name       VARCHAR(255) NOT NULL,
  email      VARCHAR(255),
  pix_key    VARCHAR(255) NOT NULL,
  active     BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_site_author_single_active
  ON payment_site_author(active) WHERE active = TRUE;

CREATE UNIQUE INDEX IF NOT EXISTS ux_site_author_email
  ON payment_site_author(email);

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_trigger WHERE tgname = 'trg_payment_site_author_updated_at'
  ) THEN
    CREATE TRIGGER trg_payment_site_author_updated_at
    BEFORE UPDATE ON payment_site_author
    FOR EACH ROW
    EXECUTE FUNCTION tg_set_updated_at();
  END IF;
END;
$$;

-- =====================================================================
-- 5) PAYMENT PAYOUTS + EMAIL
-- =====================================================================

CREATE TABLE IF NOT EXISTS payment_payouts (
  id                   BIGSERIAL PRIMARY KEY,
  amount               NUMERIC(12,2) NOT NULL,
  confirmed_at         TIMESTAMPTZ,
  created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
  efi_id_envio         VARCHAR(255),
  fail_reason          VARCHAR(255),
  order_id             BIGINT NOT NULL,
  pix_key              VARCHAR(140) NOT NULL,
  sent_at              TIMESTAMPTZ,
  status               VARCHAR(20) NOT NULL,
  amount_gross         NUMERIC(12,2) NOT NULL DEFAULT 0,
  fee_percent          NUMERIC(6,3)  NOT NULL DEFAULT 0,
  fee_fixed            NUMERIC(12,2) NOT NULL DEFAULT 0,
  margin_percent       NUMERIC(6,3)  NOT NULL DEFAULT 0,
  margin_fixed         NUMERIC(12,2) NOT NULL DEFAULT 0,
  include_gateway_fees BOOLEAN NOT NULL DEFAULT FALSE,
  amount_net           NUMERIC(12,2) NOT NULL DEFAULT 0,
  min_send             NUMERIC(12,2) NOT NULL DEFAULT 0,
  provider_ref         VARCHAR(200),
  failed_at            TIMESTAMPTZ,
  CONSTRAINT chk_payment_payout_status
    CHECK (status IN ('CREATED','SENT','CONFIRMED','FAILED','CANCELED')),
  CONSTRAINT chk_payout_pix_key_present
    CHECK (
      (status IN ('CREATED','FAILED'))
      OR (pix_key IS NOT NULL AND length(btrim(pix_key)) > 0)
    ),
  CONSTRAINT uq_payout_order UNIQUE(order_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_payment_payouts_efi_id_envio
  ON payment_payouts (efi_id_envio);

CREATE INDEX IF NOT EXISTS idx_payouts_provider_ref
  ON payment_payouts(provider_ref);

CREATE INDEX IF NOT EXISTS idx_payouts_status_created
  ON payment_payouts(status, created_at);

CREATE TABLE IF NOT EXISTS payout_email (
  id            BIGSERIAL PRIMARY KEY,
  payout_id     BIGINT,
  to_email      VARCHAR(255) NOT NULL,
  email_type    VARCHAR(40)  NOT NULL,
  sent_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
  status        VARCHAR(20)  NOT NULL,
  error_message TEXT,
  order_id      BIGINT,
  CONSTRAINT chk_payout_email_status
    CHECK (status IN ('SENT','FAILED')),
  CONSTRAINT fk_payout_email_order_id
    FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
  CONSTRAINT fk_payout_email_payout_id
    FOREIGN KEY (payout_id) REFERENCES payment_payouts(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_payout_email_order_id  ON payout_email(order_id);
CREATE INDEX IF NOT EXISTS idx_payout_email_payout_id ON payout_email(payout_id);
CREATE INDEX IF NOT EXISTS idx_payout_email_status    ON payout_email(status);
CREATE INDEX IF NOT EXISTS idx_payout_email_sent_at   ON payout_email(sent_at DESC);
CREATE INDEX IF NOT EXISTS idx_payout_email_type      ON payout_email(email_type);

-- =====================================================================
-- 6) WEBHOOKS, PAYMENT_WEBHOOKS, OUTBOX
-- =====================================================================

CREATE TABLE IF NOT EXISTS webhook_events (
  id          BIGSERIAL PRIMARY KEY,
  raw_body    TEXT,
  received_at TIMESTAMPTZ,
  status      VARCHAR(40),
  txid        VARCHAR(40),
  charge_id   VARCHAR(60),
  provider    VARCHAR(20)
);

CREATE INDEX IF NOT EXISTS idx_webhook_txid      ON webhook_events(txid);
CREATE INDEX IF NOT EXISTS idx_webhook_charge_id ON webhook_events(charge_id);
CREATE INDEX IF NOT EXISTS idx_webhook_provider  ON webhook_events(provider);

CREATE TABLE IF NOT EXISTS payment_webhook_events (
  id           BIGSERIAL PRIMARY KEY,
  event_type   VARCHAR(60) NOT NULL,
  external_id  VARCHAR(80) NOT NULL,
  order_ref    VARCHAR(80),
  payload      JSONB       NOT NULL,
  provider     VARCHAR(20) NOT NULL,
  received_at  TIMESTAMPTZ NOT NULL,
  payload_json JSONB       NOT NULL,
  CONSTRAINT uq_webhook_event_idem UNIQUE(provider, external_id, event_type)
);

CREATE TABLE IF NOT EXISTS outbox_events (
  id              UUID PRIMARY KEY,
  type            VARCHAR(128) NOT NULL,
  version         VARCHAR(16)  NOT NULL,
  author_id       UUID         NOT NULL,
  payload         JSONB        NOT NULL,
  status          VARCHAR(16)  NOT NULL,
  attempts        INTEGER      NOT NULL DEFAULT 0,
  next_attempt_at TIMESTAMPTZ  NOT NULL,
  created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
  occurred_at     TIMESTAMPTZ  NOT NULL,
  last_error      TEXT
);

CREATE INDEX IF NOT EXISTS idx_outbox_events_status_next_attempt
  ON outbox_events(status, next_attempt_at);

-- =====================================================================
-- 7) COOKIE CONSENTS
-- =====================================================================

CREATE TABLE IF NOT EXISTS cookie_consents (
  id         BIGSERIAL PRIMARY KEY,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  ip_hash    TEXT        NOT NULL,
  user_agent TEXT        NOT NULL,
  prefs      JSONB       NOT NULL,
  source     TEXT        NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_cookie_consents_created_at
  ON cookie_consents(created_at);

-- =====================================================================
-- 8) PAYMENT_BOOK_AUTHORS (módulo de repasse)
-- =====================================================================

CREATE TABLE IF NOT EXISTS payment_book_authors (
  book_id   VARCHAR(255) PRIMARY KEY,
  author_id BIGINT NOT NULL,
  CONSTRAINT payment_book_authors_book_id_fkey
    FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE CASCADE,
  CONSTRAINT payment_book_authors_author_id_fkey
    FOREIGN KEY (author_id) REFERENCES payment_author_registry(id)
);

CREATE INDEX IF NOT EXISTS idx_pba_author_id ON payment_book_authors(author_id);

-- =====================================================================
-- 9) SEED BÁSICO DO AUTOR DO SITE (id = 1)
-- =====================================================================

-- Usa placeholders do Flyway:
--  SITE_AUTHOR_NAME
--  SITE_AUTHOR_EMAIL
--  SITE_AUTHOR_PIX_KEY

INSERT INTO authors (id, name, email)
VALUES (1, '${SITE_AUTHOR_NAME}', '${SITE_AUTHOR_EMAIL}')
ON CONFLICT (id) DO UPDATE
  SET name  = EXCLUDED.name,
      email = EXCLUDED.email;

INSERT INTO payment_author_registry (id, name, email)
VALUES (1, '${SITE_AUTHOR_NAME}', '${SITE_AUTHOR_EMAIL}')
ON CONFLICT (id) DO UPDATE
  SET name  = EXCLUDED.name,
      email = EXCLUDED.email;

INSERT INTO payment_author_accounts (author_id, pix_key)
VALUES (1, '${SITE_AUTHOR_PIX_KEY}')
ON CONFLICT (author_id) DO UPDATE
  SET pix_key = EXCLUDED.pix_key;

INSERT INTO payment_site_author (id, name, email, pix_key, active)
VALUES (1, '${SITE_AUTHOR_NAME}', '${SITE_AUTHOR_EMAIL}', '${SITE_AUTHOR_PIX_KEY}', TRUE)
ON CONFLICT (id) DO UPDATE
  SET name   = EXCLUDED.name,
      email  = EXCLUDED.email,
      pix_key = EXCLUDED.pix_key,
      active = EXCLUDED.active;

-- Garante que só o registro 1 está ativo
UPDATE payment_site_author
SET active = (id = 1);
