-- ============================================================================
-- The names PostgreSQL generated on its own, moved to English.
--
-- V6 renamed the tables, the columns and the three constraints that V1 and V2
-- had NAMED explicitly. It missed the ones nobody ever wrote down: primary
-- keys, unique keys, foreign keys and sequences get their name from the table
-- they were born on, and a rename of the table does not follow them. The
-- result is a constraint called aule_nome_key sitting on a table called rooms,
-- which is exactly the leftover that costs somebody time the day a database
-- error names it.
--
-- WHY A NEW MIGRATION INSTEAD OF FIXING V6: V6 is already committed. Flyway
-- checksums what it has applied, so editing it would break validation on any
-- database where it has already run. A new file costs one number.
--
-- WHY EVERY RENAME IS GUARDED: these names are only predictable on a database
-- that Flyway itself created. On a database that predates Flyway, V1 was
-- marked as applied without running (baseline-on-migrate), so the schema came
-- from Hibernate's ddl-auto - and Hibernate names a unique key uk_<hash>, not
-- aule_nome_key. An unguarded ALTER TABLE ... RENAME CONSTRAINT would abort
-- the migration there, taking the service's startup down with it. Guarded, the
-- worst case is that this migration does nothing, which is the right outcome:
-- a name that was never Italian needs no translation.
-- ============================================================================

DO $$
DECLARE
    renames text[][] := ARRAY[
        -- table       old name                      new name
        ['rooms',    'aule_pkey',                  'rooms_pkey'],
        ['rooms',    'aule_nome_key',              'rooms_name_key'],
        ['courses',  'corsi_pkey',                 'courses_pkey'],
        ['bookings', 'prenotazioni_pkey',          'bookings_pkey'],
        ['bookings', 'prenotazioni_aula_id_fkey',  'bookings_room_id_fkey'],
        ['bookings', 'prenotazioni_corso_id_fkey', 'bookings_course_id_fkey']
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

-- The sequences behind the bigserial columns. Renaming a sequence does not
-- disturb the column default that calls it: the default stores a resolved
-- reference, not the text of the name.
ALTER SEQUENCE IF EXISTS aule_id_seq         RENAME TO rooms_id_seq;
ALTER SEQUENCE IF EXISTS corsi_id_seq        RENAME TO courses_id_seq;
ALTER SEQUENCE IF EXISTS prenotazioni_id_seq RENAME TO bookings_id_seq;

-- NOT NULL constraints, which only exist under a name from PostgreSQL 17 on.
-- Before 17 they were a column flag with nothing to rename, so on the pinned
-- postgres:16 this loop matches nothing and does nothing. On 17 and later a
-- database built from V1 carries aule_nome_not_null on a table called rooms,
-- for the same reason as everything above.
--
-- Derived rather than listed: the name is always <table>_<column>_not_null, so
-- rebuilding it from the catalogue means this keeps working if a column is
-- added later, and renames exactly the ones that drifted.
DO $$
DECLARE
    r record;
BEGIN
    FOR r IN
        SELECT t.relname AS tbl,
               c.conname AS old_name,
               t.relname || '_' || a.attname || '_not_null' AS new_name
          FROM pg_constraint c
          JOIN pg_class t     ON t.oid = c.conrelid
          JOIN pg_namespace n ON n.oid = t.relnamespace
          JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = c.conkey[1]
         WHERE n.nspname = 'public'
           AND c.contype = 'n'
           AND t.relname IN ('rooms', 'courses', 'bookings')
           AND c.conname <> t.relname || '_' || a.attname || '_not_null'
    LOOP
        EXECUTE format('ALTER TABLE %I RENAME CONSTRAINT %I TO %I',
                       r.tbl, r.old_name, r.new_name);
    END LOOP;
END $$;
