-- ============================================================================
-- The names PostgreSQL generated on its own, moved to English.
-- Same reasoning and same guards as V7 of prenotazione-service.
--
-- V2 already renamed the two indexes, because V1 had named them explicitly.
-- What was left is what V1 never named: the primary key and the sequence
-- behind the bigserial id, both still carrying "notifiche".
--
-- There is no foreign key to move: this table dropped its REFERENCES to
-- utenti when notifications became a service of their own, which is why
-- user_id here is a plain bigint.
-- ============================================================================

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
          FROM pg_constraint c
          JOIN pg_class t ON t.oid = c.conrelid
         WHERE t.relname = 'notifications'
           AND c.conname = 'notifiche_pkey'
    ) THEN
        ALTER TABLE notifications RENAME CONSTRAINT notifiche_pkey TO notifications_pkey;
    END IF;
END $$;

ALTER SEQUENCE IF EXISTS notifiche_id_seq RENAME TO notifications_id_seq;

-- NOT NULL constraints, named only from PostgreSQL 17 on. A no-op on the
-- pinned postgres:16, where they are a column flag with no name to move.
DO $$
DECLARE
    r record;
BEGIN
    FOR r IN
        SELECT c.conname AS old_name,
               'notifications_' || a.attname || '_not_null' AS new_name
          FROM pg_constraint c
          JOIN pg_class t     ON t.oid = c.conrelid
          JOIN pg_namespace n ON n.oid = t.relnamespace
          JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = c.conkey[1]
         WHERE n.nspname = 'public'
           AND c.contype = 'n'
           AND t.relname = 'notifications'
           AND c.conname <> 'notifications_' || a.attname || '_not_null'
    LOOP
        EXECUTE format('ALTER TABLE notifications RENAME CONSTRAINT %I TO %I',
                       r.old_name, r.new_name);
    END LOOP;
END $$;
