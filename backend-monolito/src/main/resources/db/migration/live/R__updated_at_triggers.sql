-- R__updated_at_triggers.sql
-- Garante a função e (re)assegura os triggers de updated_at sem quebrar nada.

-- 1) Função utilitária
CREATE OR REPLACE FUNCTION tg_set_updated_at()
RETURNS trigger AS $$
BEGIN
  NEW.updated_at := NOW();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 2) Reforço idempotente dos triggers (se as tabelas existirem)
DO $$
BEGIN
  IF to_regclass('public.payment_site_author') IS NOT NULL THEN
    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname='trg_payment_site_author_updated_at') THEN
      CREATE TRIGGER trg_payment_site_author_updated_at
        BEFORE UPDATE ON payment_site_author
        FOR EACH ROW EXECUTE FUNCTION tg_set_updated_at();
    END IF;
  END IF;

  IF to_regclass('public.payment_author_registry') IS NOT NULL THEN
    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname='trg_payment_author_registry_updated_at') THEN
      CREATE TRIGGER trg_payment_author_registry_updated_at
        BEFORE UPDATE ON payment_author_registry
        FOR EACH ROW EXECUTE FUNCTION tg_set_updated_at();
    END IF;
  END IF;
END$$;
