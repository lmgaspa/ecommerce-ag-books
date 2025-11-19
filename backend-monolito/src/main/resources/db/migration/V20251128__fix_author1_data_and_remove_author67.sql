-- V20251128__fix_author1_data_and_remove_author67.sql
-- 1) Corrige dados do author_id = 1: nome, email e pix_key
-- 2) Remove author_id = 67 de todas as tabelas relacionadas (limpeza)
-- Idempotente: pode rodar múltiplas vezes sem problemas

DO $$
DECLARE
  v_email_67 text;
BEGIN
  --------------------------------------------------------------------
  -- 1) Atualiza dados do author_id = 1 em payment_author_registry
  --    (idempotente: só atualiza se diferente)
  --    Primeiro resolve conflito de email único
  --------------------------------------------------------------------
  -- 1.1 Se existe outro registro com email ag1957@gmail.com (que não seja id=1), resolve conflito
  IF EXISTS (SELECT 1 FROM payment_author_registry WHERE lower(email) = lower('ag1957@gmail.com') AND id <> 1) THEN
    -- Atualiza o email do outro registro para um valor temporário para liberar o email
    UPDATE payment_author_registry 
       SET email = 'ag1957@gmail.com.tmp' || id::text || '@migration.fix',
           updated_at = NOW()
     WHERE lower(email) = lower('ag1957@gmail.com') AND id <> 1;
  END IF;

  -- 1.2 Agora atualiza ou cria o author_id = 1
  IF EXISTS (SELECT 1 FROM payment_author_registry WHERE id = 1) THEN
    -- Atualiza apenas se os dados forem diferentes
    UPDATE payment_author_registry
       SET name  = 'Agenor Gasparetto',
           email = 'ag1957@gmail.com',
           updated_at = NOW()
     WHERE id = 1
       AND (name <> 'Agenor Gasparetto' OR lower(email) <> lower('ag1957@gmail.com'));
  ELSE
    -- Se não existir, cria (fallback)
    INSERT INTO payment_author_registry (id, name, email, author_uuid, created_at, updated_at)
    VALUES (1, 'Agenor Gasparetto', 'ag1957@gmail.com', gen_random_uuid(), NOW(), NOW())
    ON CONFLICT (id) DO UPDATE
      SET name  = EXCLUDED.name,
          email = EXCLUDED.email,
          updated_at = NOW();
  END IF;

  --------------------------------------------------------------------
  -- 2) Atualiza/cria conta PIX do author_id = 1 em payment_author_accounts
  --    (idempotente: UPSERT)
  --------------------------------------------------------------------
  INSERT INTO payment_author_accounts (author_id, pix_key)
  VALUES (1, '293.220.220.00')
  ON CONFLICT (author_id) DO UPDATE
    SET pix_key = EXCLUDED.pix_key
    WHERE payment_author_accounts.pix_key <> '293.220.220.00';

  --------------------------------------------------------------------
  -- 3) Remove author_id = 67 de todas as tabelas relacionadas
  --    (idempotente: só remove se existir, ordem respeitando FKs)
  --------------------------------------------------------------------
  -- 3.1 Busca email do author_id = 67 antes de deletar (para payment_site_author)
  SELECT email INTO v_email_67 FROM payment_author_registry WHERE id = 67 LIMIT 1;

  -- 3.2 Remove de payment_book_authors (FK para payment_author_registry, remove primeiro)
  IF EXISTS (SELECT 1 FROM payment_book_authors WHERE author_id = 67) THEN
    DELETE FROM payment_book_authors WHERE author_id = 67;
  END IF;

  -- 3.3 Remove de payment_author_accounts (FK para payment_author_registry)
  IF EXISTS (SELECT 1 FROM payment_author_accounts WHERE author_id = 67) THEN
    DELETE FROM payment_author_accounts WHERE author_id = 67;
  END IF;

  -- 3.4 Remove de payment_site_author (se existir email relacionado)
  IF v_email_67 IS NOT NULL AND EXISTS (SELECT 1 FROM payment_site_author WHERE lower(email) = lower(v_email_67)) THEN
    DELETE FROM payment_site_author WHERE lower(email) = lower(v_email_67);
  END IF;

  -- 3.5 Remove de authors (tabela legada, se existir)
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'authors') THEN
    DELETE FROM authors WHERE id = 67;
  END IF;

  -- 3.6 Remove de payment_author_registry (último, devido a FKs)
  IF EXISTS (SELECT 1 FROM payment_author_registry WHERE id = 67) THEN
    DELETE FROM payment_author_registry WHERE id = 67;
  END IF;

END $$;

