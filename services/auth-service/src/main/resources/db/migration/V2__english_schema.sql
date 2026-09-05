-- ============================================================================
-- Lo schema degli utenti passa all'inglese, per combaciare con il codice.
-- Stessa ragione e stesse cautele della V6 di prenotazione-service: migrazione
-- nuova e non modifica alla V1, perche' Flyway ne verifica il checksum; RENAME e
-- non DROP/CREATE, perche' i dati restano dove sono.
-- ============================================================================

ALTER TABLE utenti RENAME COLUMN nome               TO name;
ALTER TABLE utenti RENAME COLUMN ruolo              TO role;
ALTER TABLE utenti RENAME COLUMN data_registrazione TO registered_at;
ALTER TABLE utenti RENAME COLUMN ultimo_accesso     TO last_login;
ALTER TABLE utenti RENAME TO users;

-- Il nome del vincolo non segue quello della colonna.
ALTER TABLE users RENAME CONSTRAINT utente_ruolo_check TO user_role_check;
