-- ============================================================================
-- The notification schema moves to English, to line up with the code.
-- Same reasoning and same caution as booking-service's V6.
-- ============================================================================

-- "letta" becomes is_read and not read: READ is a keyword in SQL, and a column that always
-- has to be quoted is a trap for the next query written by hand. is_read also follows the
-- shape the rooms table already used for is_virtual.
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

-- Indexes survive a column rename, but they keep their own name.
ALTER INDEX idx_notifiche_utente           RENAME TO idx_notifications_user;
ALTER INDEX idx_notifiche_utente_non_lette RENAME TO idx_notifications_user_unread;
