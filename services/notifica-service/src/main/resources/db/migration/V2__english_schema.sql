-- ============================================================================
-- Lo schema delle notifiche passa all'inglese, per combaciare con il codice.
-- Stessa ragione e stesse cautele della V6 di prenotazione-service.
-- ============================================================================

-- "letta" diventa is_read e non read: READ e' una parola chiave in SQL, e una
-- colonna che va sempre virgolettata e' una trappola per la prossima query scritta
-- a mano. is_read segue anche la forma che la tabella aule usava gia' per is_virtual.
ALTER TABLE notifiche RENAME COLUMN utente_id         TO user_id;
ALTER TABLE notifiche RENAME COLUMN titolo            TO title;
ALTER TABLE notifiche RENAME COLUMN messaggio         TO message;
ALTER TABLE notifiche RENAME COLUMN tipo              TO type;
ALTER TABLE notifiche RENAME COLUMN letta             TO is_read;
ALTER TABLE notifiche RENAME COLUMN data_creazione    TO created_at;
ALTER TABLE notifiche RENAME COLUMN data_lettura      TO read_at;
ALTER TABLE notifiche RENAME COLUMN prenotazione_id   TO booking_id;
ALTER TABLE notifiche RENAME COLUMN nome_stanza       TO room_name;
ALTER TABLE notifiche RENAME COLUMN data_prenotazione TO booking_date;
ALTER TABLE notifiche RENAME COLUMN admin_nome        TO admin_name;
ALTER TABLE notifiche RENAME TO notifications;

-- Gli indici sopravvivono al rename delle colonne, ma conservano il proprio nome.
ALTER INDEX idx_notifiche_utente           RENAME TO idx_notifications_user;
ALTER INDEX idx_notifiche_utente_non_lette RENAME TO idx_notifications_user_unread;
