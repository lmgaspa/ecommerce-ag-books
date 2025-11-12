-- R__updated_at_triggers.sql
-- Repeatable: cria/garante a função tg_set_updated_at() e instala triggers
-- de updated_at apenas quando necessário (idempotente).

-- 1) Função genérica para manter updated_at
CREATE OR REPLACE FUNCTION tg_set_updated_at()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  NEW.updated_at := NOW();
  RETURN NEW;
END;
$$;

-- 2) Helper: instala trigger se a tabela existir e a trigger ainda não existir
DO $$
DECLARE
  v_exists BOOL;
BEGIN
  -- payment_site_author
  IF to_regclass('public.payment_site_author') IS NOT NULL THEN
    SELECT EXISTS(
      SELECT 1 FROM pg_trigger WHERE tgname = 'trg_payment_site_author_updated_at'
    ) INTO v_exists;

    IF NOT v_exists THEN
      EXECUTE $sql$
        CREATE TRIGGER trg_payment_site_author_updated_at
        BEFORE UPDATE ON public.payment_site_author
        FOR EACH ROW EXECUTE FUNCTION tg_set_updated_at()
      $sql$;
    END IF;
  END IF;

  -- payment_author_registry
  IF to_regclass('public.payment_author_registry') IS NOT NULL THEN
    SELECT EXISTS(
      SELECT 1 FROM pg_trigger WHERE tgname = 'trg_payment_author_registry_updated_at'
    ) INTO v_exists;

    IF NOT v_exists THEN
      EXECUTE $sql$
        CREATE TRIGGER trg_payment_author_registry_updated_at
        BEFORE UPDATE ON public.payment_author_registry
        FOR EACH ROW EXECUTE FUNCTION tg_set_updated_at()
      $sql$;
    END IF;
  END IF;

  -- coupons
  IF to_regclass('public.coupons') IS NOT NULL
     AND EXISTS (
       SELECT 1 FROM information_schema.columns
       WHERE table_schema='public' AND table_name='coupons' AND column_name='updated_at'
     )
  THEN
    SELECT EXISTS(
      SELECT 1 FROM pg_trigger WHERE tgname = 'trg_coupons_updated_at'
    ) INTO v_exists;

    IF NOT v_exists THEN
      EXECUTE $sql$
        CREATE TRIGGER trg_coupons_updated_at
        BEFORE UPDATE ON public.coupons
        FOR EACH ROW EXECUTE FUNCTION tg_set_updated_at()
      $sql$;
    END IF;
  END IF;

  -- order_coupons
  IF to_regclass('public.order_coupons') IS NOT NULL
     AND EXISTS (
       SELECT 1 FROM information_schema.columns
       WHERE table_schema='public' AND table_name='order_coupons' AND column_name='updated_at'
     )
  THEN
    SELECT EXISTS(
      SELECT 1 FROM pg_trigger WHERE tgname = 'trg_order_coupons_updated_at'
    ) INTO v_exists;

    IF NOT v_exists THEN
      EXECUTE $sql$
        CREATE TRIGGER trg_order_coupons_updated_at
        BEFORE UPDATE ON public.order_coupons
        FOR EACH ROW EXECUTE FUNCTION tg_set_updated_at()
      $sql$;
    END IF;
  END IF;

  -- (adicione outras tabelas com coluna updated_at aqui se precisar)
END;
$$;
