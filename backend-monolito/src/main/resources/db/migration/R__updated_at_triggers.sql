-- R__updated_at_triggers.sql
-- Garante a função e (re)assegura os triggers de updated_at sem quebrar nada.

-- 1) Função utilitária (schema-qualificada + search_path neutro + "mudou?")
CREATE OR REPLACE FUNCTION public.tg_set_updated_at()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  -- Evita "atualizar sem mudança" e loops desnecessários
  IF NEW IS DISTINCT FROM OLD THEN
    NEW.updated_at := NOW();
  END IF;
  RETURN NEW;
END;
$$;

-- Segurança: evita surpresas com search_path em runtime
ALTER FUNCTION public.tg_set_updated_at() SET search_path = public;

-- 2) Reforço idempotente dos triggers (se as tabelas existirem)
DO $$
BEGIN
  IF to_regclass('public.payment_site_author') IS NOT NULL
     AND EXISTS (
       SELECT 1 FROM information_schema.columns
       WHERE table_schema='public' AND table_name='payment_site_author' AND column_name='updated_at'
     )
  THEN
    -- recria de forma idempotente
    IF EXISTS (SELECT 1 FROM pg_trigger WHERE tgname='trg_payment_site_author_updated_at') THEN
      EXECUTE 'DROP TRIGGER trg_payment_site_author_updated_at ON public.payment_site_author';
    END IF;
    EXECUTE '
      CREATE TRIGGER trg_payment_site_author_updated_at
      BEFORE UPDATE ON public.payment_site_author
      FOR EACH ROW EXECUTE FUNCTION public.tg_set_updated_at()';
  END IF;

  IF to_regclass('public.payment_author_registry') IS NOT NULL
     AND EXISTS (
       SELECT 1 FROM information_schema.columns
       WHERE table_schema='public' AND table_name='payment_author_registry' AND column_name='updated_at'
     )
  THEN
    IF EXISTS (SELECT 1 FROM pg_trigger WHERE tgname='trg_payment_author_registry_updated_at') THEN
      EXECUTE 'DROP TRIGGER trg_payment_author_registry_updated_at ON public.payment_author_registry';
    END IF;
    EXECUTE '
      CREATE TRIGGER trg_payment_author_registry_updated_at
      BEFORE UPDATE ON public.payment_author_registry
      FOR EACH ROW EXECUTE FUNCTION public.tg_set_updated_at()';
  END IF;
END$$;
