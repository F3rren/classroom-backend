-- ============================================================================
-- The names PostgreSQL generated on its own, moved to English.
-- Same reasoning and same guards as V7 of prenotazione-service.
--
-- V2 renamed the table, the columns and utente_ruolo_check, which V1 had named
-- explicitly. The primary key, the two unique keys and the sequence were never
-- written down anywhere: they took their name from "utenti" and stayed behind
-- when the table became "users".
--
-- The guard matters more here than anywhere else: a duplicate email is a case
-- this service hits in normal use, so users_email_key is a name that reaches a
-- log line regularly.
-- ============================================================================

DO $$
DECLARE
    renames text[][] := ARRAY[
        -- table   old name                new name
        ['users', 'utenti_pkey',         'users_pkey'],
        ['users', 'utenti_username_key', 'users_username_key'],
        ['users', 'utenti_email_key',    'users_email_key']
    ];
    i int;
BEGIN
    FOR i IN 1 .. array_length(renames, 1) LOOP
        IF EXISTS (
            SELECT 1
              FROM pg_constraint c
              JOIN pg_class t ON t.oid = c.conrelid
             WHERE t.relname = renames[i][1]
               AND c.conname = renames[i][2]
        ) THEN
            EXECUTE format('ALTER TABLE %I RENAME CONSTRAINT %I TO %I',
                           renames[i][1], renames[i][2], renames[i][3]);
        END IF;
    END LOOP;
END $$;

ALTER SEQUENCE IF EXISTS utenti_id_seq RENAME TO users_id_seq;

-- NOT NULL constraints, named only from PostgreSQL 17 on. A no-op on the
-- pinned postgres:16, where they are a column flag with no name to move.
DO $$
DECLARE
    r record;
BEGIN
    FOR r IN
        SELECT c.conname AS old_name,
               'users_' || a.attname || '_not_null' AS new_name
          FROM pg_constraint c
          JOIN pg_class t     ON t.oid = c.conrelid
          JOIN pg_namespace n ON n.oid = t.relnamespace
          JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = c.conkey[1]
         WHERE n.nspname = 'public'
           AND c.contype = 'n'
           AND t.relname = 'users'
           AND c.conname <> 'users_' || a.attname || '_not_null'
    LOOP
        EXECUTE format('ALTER TABLE users RENAME CONSTRAINT %I TO %I',
                       r.old_name, r.new_name);
    END LOOP;
END $$;
