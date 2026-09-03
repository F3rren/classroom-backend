-- ============================================================================
-- V2 - Vincoli di dominio su stato e ruolo.
--
-- Questi CHECK erano documentati nei file in src/main/java/com/prenotazioni/sql/,
-- ma quei file non venivano MAI eseguiti: Spring esegue automaticamente solo
-- schema.sql/data.sql in resources, e Maven non copia nemmeno i .sql presenti
-- sotto src/main/java. Lo schema reale era quindi quello generato da Hibernate,
-- privo di questi vincoli: una verifica sul database ha confermato 0 CHECK
-- presenti, e un INSERT con ruolo 'SUPERUSER' veniva accettato senza errori.
--
-- Sono i valori attesi dagli enum StatoPrenotazione, StatoAula e Ruolo, che gia'
-- li impongono lato applicazione. Qui diventano una garanzia anche per chi scrive
-- sul database aggirando l'applicazione (script manuali, import, altri servizi).
--
-- Migrazione sicura sui dati esistenti: i valori presenti sono stati verificati
-- prima di scriverla e rientrano tutti negli insiemi ammessi.
-- ============================================================================

ALTER TABLE utenti
    ADD CONSTRAINT utente_ruolo_check
    CHECK (ruolo IN ('admin', 'user'));

ALTER TABLE aule
    ADD CONSTRAINT aula_stato_check
    CHECK (stato IN ('libera', 'occupata', 'bloccata', 'manutenzione'));

-- 'confermata' non e' mai assegnata dal codice ma resta ammessa: e' storicamente
-- prevista e una riga legacy con quel valore deve poter essere letta. StatoPrenotazione
-- la include per lo stesso motivo.
ALTER TABLE prenotazioni
    ADD CONSTRAINT prenotazione_stato_check
    CHECK (stato IN ('prenotata', 'confermata', 'bloccata', 'manutenzione', 'annullata'));
