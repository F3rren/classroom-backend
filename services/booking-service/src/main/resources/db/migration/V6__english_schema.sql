-- ============================================================================
-- Lo schema passa all'inglese, per combaciare con il codice.
--
-- Il progetto ha una regola sola: tutto cio' che legge un programmatore e' in
-- inglese, e resta in italiano solo il testo mostrato a chi usa il sistema. Le
-- tabelle e le colonne le legge un programmatore, quindi seguono il codice.
--
-- PERCHE' UNA MIGRAZIONE NUOVA E NON UNA MODIFICA ALLE PRECEDENTI: Flyway
-- verifica il checksum di cio' che ha gia' applicato. Cambiare una V1 gia'
-- eseguita farebbe fallire la validazione su ogni database esistente - e non
-- rinominerebbe nulla su quelli, perche' quella migrazione non verrebbe rieseguita.
--
-- RENAME e non DROP/CREATE: i dati restano dove sono. PostgreSQL aggiorna da se'
-- indici, chiavi esterne e definizioni dei vincoli che citano le colonne; i NOMI
-- dei vincoli invece no, e vanno spostati a mano - e' quello che fa l'ultima parte.
-- ============================================================================

-- ---------------------------------------------------------------- aule -> rooms
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
-- inizio/fine diventano start_time/end_time e non start/end: "end" e' una parola
-- riservata in SQL, e una colonna che va sempre virgolettata e' una trappola per
-- chi scrivera' la prossima query a mano.
ALTER TABLE prenotazioni RENAME COLUMN aula_id         TO room_id;
ALTER TABLE prenotazioni RENAME COLUMN corso_id        TO course_id;
ALTER TABLE prenotazioni RENAME COLUMN utente_id       TO user_id;
ALTER TABLE prenotazioni RENAME COLUMN inizio          TO start_time;
ALTER TABLE prenotazioni RENAME COLUMN fine            TO end_time;
ALTER TABLE prenotazioni RENAME COLUMN stato           TO status;
ALTER TABLE prenotazioni RENAME COLUMN descrizione     TO description;
ALTER TABLE prenotazioni RENAME COLUMN data_creazione  TO created_at;
-- Aggiunte dalla V4, quando il proprietario e' stato denormalizzato.
ALTER TABLE prenotazioni RENAME COLUMN utente_nome     TO user_name;
ALTER TABLE prenotazioni RENAME COLUMN utente_username TO user_username;
ALTER TABLE prenotazioni RENAME TO bookings;

-- ------------------------------------------------------- nomi dei vincoli
-- Non seguono il rename delle colonne: restano quelli con cui sono nati, e un
-- vincolo che si chiama aula_stato_check su una tabella "rooms" e' esattamente il
-- tipo di residuo che fa perdere tempo a chi legge un errore del database.
ALTER TABLE rooms    RENAME CONSTRAINT aula_stato_check         TO room_status_check;
ALTER TABLE bookings RENAME CONSTRAINT prenotazione_stato_check TO booking_status_check;
ALTER TABLE bookings RENAME CONSTRAINT prenotazioni_no_overlap  TO bookings_no_overlap;
