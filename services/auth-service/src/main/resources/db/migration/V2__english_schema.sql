-- ============================================================================
-- The user schema moves to English, to line up with the code.
-- Same reasoning and same caution as booking-service's V6: a new migration rather than a
-- change to V1, because Flyway checks its checksum; RENAME and not DROP/CREATE, because the
-- data stays where it is.
-- ============================================================================

ALTER TABLE utenti RENAME COLUMN nome               TO name;
ALTER TABLE utenti RENAME COLUMN ruolo              TO role;
ALTER TABLE utenti RENAME COLUMN data_registrazione TO registered_at;
ALTER TABLE utenti RENAME COLUMN ultimo_accesso     TO last_login;
ALTER TABLE utenti RENAME TO users;

-- A constraint's name does not follow its column's.
ALTER TABLE users RENAME CONSTRAINT utente_ruolo_check TO user_role_check;
