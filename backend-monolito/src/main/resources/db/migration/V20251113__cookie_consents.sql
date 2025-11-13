CREATE TABLE IF NOT EXISTS cookie_consents (
  id BIGSERIAL PRIMARY KEY,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  ip_hash TEXT NOT NULL,
  user_agent TEXT NOT NULL,
  prefs JSONB NOT NULL,
  source TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_cookie_consents_created_at
    ON cookie_consents(created_at);

