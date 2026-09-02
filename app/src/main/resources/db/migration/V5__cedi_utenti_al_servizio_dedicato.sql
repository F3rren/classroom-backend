-- ============================================================================
-- La tabella utenti passa ad auth-service.
--
-- La V4 aveva gia' tolto la chiave esterna e copiato username e nome sulle
-- prenotazioni, quindi qui non si perde nulla che serva ancora: prenotazioni
-- conserva utente_id, utente_username e utente_nome come istantanea.
--
-- Da questo punto in poi l'unica definizione della tabella utenti e' la V1 di
-- auth-service. Tenerne una copia qui significherebbe due schemi da mantenere
-- allineati a mano, che e' esattamente cio' che la separazione deve evitare.
--
-- I dati non vanno persi: prima di applicare questa migrazione su un database
-- gia' popolato, travasare utenti nel database di auth-service, per esempio con
--     pg_dump -t utenti prenotazione_aule | psql prenotazione_aule_utenti
-- Su un database vuoto non c'e' nulla da travasare.
-- ============================================================================

DROP TABLE IF EXISTS utenti;
