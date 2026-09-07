-- ============================================================================
-- The schema moves to English, to line up with the code.
--
-- The project has one rule: everything a programmer reads is in English, and only the text
-- shown to whoever uses the system stays Italian. Tables and columns are read by a
-- programmer, so they follow the code.
--
-- PERCHE' UNA MIGRAZIONE NUOVA E NON UNA MODIFICA ALLE PRECEDENTI: Flyway
-- checks the checksum of what it has already applied. Changing a V1 that has already run
-- would fail validation on every existing database - and would rename nothing there, because
-- that migration would not be re-executed.
--
-- RENAME and not DROP/CREATE: the data stays where it is. PostgreSQL updates the indexes,
-- foreign keys and constraint definitions that mention the columns by itself; the constraint
-- NAMES it does not, and those have to be moved by hand - that is what the last part does.
-- ============================================================================

-- ---------------------------------------------------------------- aule -> rooms (table)
ALTER TABLE aule RENAME COLUMN nome        TO name;
ALTER TABLE aule RENAME COLUMN capienza    TO capacity;
ALTER TABLE aule RENAME COLUMN piano       TO floor;
ALTER TABLE aule RENAME COLUMN descrizione TO description;
ALTER TABLE aule RENAME COLUMN stato       TO status;
ALTER TABLE aule RENAME TO rooms;

-- ------------------------------------------------------------- corsi -> courses
ALTER TABLE corsi RENAME COLUMN nome        TO name;
ALTER TABLE corsi RENAME COLUMN docente     TO teacher;
ALTER TABLE corsi RENAME COLUMN descrizione TO description;
ALTER TABLE corsi RENAME TO courses;

-- ------------------------------------------------- prenotazioni -> bookings
-- inizio/fine become start_time/end_time and not start/end: "end" is a reserved word in SQL,
-- and a column that always has to be quoted is a trap for whoever writes the next query by
-- hand.
ALTER TABLE prenotazioni RENAME COLUMN aula_id         TO room_id;
ALTER TABLE prenotazioni RENAME COLUMN corso_id        TO course_id;
ALTER TABLE prenotazioni RENAME COLUMN utente_id       TO user_id;
ALTER TABLE prenotazioni RENAME COLUMN inizio          TO start_time;
ALTER TABLE prenotazioni RENAME COLUMN fine            TO end_time;
ALTER TABLE prenotazioni RENAME COLUMN stato           TO status;
ALTER TABLE prenotazioni RENAME COLUMN descrizione     TO description;
ALTER TABLE prenotazioni RENAME COLUMN data_creazione  TO created_at;
-- Added by V4, when the owner was denormalised.
ALTER TABLE prenotazioni RENAME COLUMN utente_nome     TO user_name;
ALTER TABLE prenotazioni RENAME COLUMN utente_username TO user_username;
ALTER TABLE prenotazioni RENAME TO bookings;

-- ------------------------------------------------------- constraint names
-- They do not follow the column renames: they keep the names they were born with, and a
-- constraint called aula_stato_check on a table named "rooms" is exactly the sort of leftover
-- that costs somebody time when they read a database error.
ALTER TABLE rooms    RENAME CONSTRAINT aula_stato_check         TO room_status_check;
ALTER TABLE bookings RENAME CONSTRAINT prenotazione_stato_check TO booking_status_check;
ALTER TABLE bookings RENAME CONSTRAINT prenotazioni_no_overlap  TO bookings_no_overlap;
