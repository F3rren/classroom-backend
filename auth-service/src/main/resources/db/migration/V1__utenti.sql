-- ============================================================================
-- Lo schema del servizio utenti: una tabella sola.
--
-- E' la stessa definizione che stava nella V1 del monolite, portata qui insieme
-- al CHECK sul ruolo che stava nella V2. Non e' una copia da tenere allineata:
-- da adesso questa e' l'unica definizione, e la tabella utenti sparisce dallo
-- schema di booking-service.
--
-- Le prenotazioni e le notifiche conservano un utente_id senza chiave esterna,
-- perche' vivono in altri database. Nessuna FK puo' attraversare quel confine.
-- ============================================================================

CREATE TABLE IF NOT EXISTS utenti (
    id                 bigserial PRIMARY KEY,
    username           varchar(50)  NOT NULL UNIQUE,
    nome               varchar(100) NOT NULL,
    email              varchar(100) NOT NULL UNIQUE,
    password           varchar(255) NOT NULL,
    ruolo              varchar(20)  NOT NULL,
    data_registrazione timestamp    NOT NULL,
    ultimo_accesso     timestamp
);

-- Tiene il dominio del ruolo allineato all'enum Ruolo. RuoloTest cicla i valori
-- dell'enum proprio per intercettare una costante aggiunta senza migrazione.
ALTER TABLE utenti
    DROP CONSTRAINT IF EXISTS utente_ruolo_check;

ALTER TABLE utenti
    ADD CONSTRAINT utente_ruolo_check
    CHECK (ruolo IN ('admin', 'user'));
