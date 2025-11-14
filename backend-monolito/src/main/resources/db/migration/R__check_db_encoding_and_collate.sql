-- R__check_db_encoding_and_collate.sql
-- Falha o deploy se Database não estiver com (UTF8, en_US.UTF-8)
DO $$
DECLARE
    v_dbname     text;
    v_encoding   text;
    v_collate    text;
    v_ctype      text;
BEGIN
    SELECT datname, pg_encoding_to_char(encoding), datcollate, datctype
      INTO v_dbname, v_encoding, v_collate, v_ctype
      FROM pg_database
     WHERE datname = current_database();

    IF v_encoding <> 'UTF8' THEN
        RAISE EXCEPTION 'Database % has encoding %, expected UTF8', v_dbname, v_encoding;
    END IF;

    IF v_collate <> 'en_US.UTF-8' OR v_ctype <> 'en_US.UTF-8' THEN
        RAISE EXCEPTION 'Database % has collation/ctype %/%, expected en_US.UTF-8/en_US.UTF-8',
            v_dbname, v_collate, v_ctype;
    END IF;
END $$;
