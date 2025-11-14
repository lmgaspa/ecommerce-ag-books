-- Se ICU estiver disponível, cria um apelido "en_us_icu" para usar nas colunas
DO $$
BEGIN
    -- Alguns providers não permitem CREATE COLLATION; por isso o TRY/CATCH
    BEGIN
        CREATE COLLATION IF NOT EXISTS en_us_icu (provider = icu, locale = 'en-US');
    EXCEPTION WHEN OTHERS THEN
        -- ignora silenciosamente se não for suportado
        PERFORM 1;
    END;
END $$;
