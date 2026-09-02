-- ============================================================================
-- Schema del servizio notifiche.
--
-- Estratto da V1__baseline_schema.sql del monolite, con una differenza che va
-- capita e non subita: utente_id NON ha piu' REFERENCES utenti (id).
--
-- La tabella utenti appartiene a un altro servizio e vivra' in un altro database,
-- quindi la chiave esterna non e' esprimibile. Prima il database garantiva che non
-- potessero esistere notifiche per un utente inesistente; ora quella garanzia e'
-- applicativa, ed e' uno dei costi reali della separazione.
--
-- L'indice su utente_id, che la chiave esterna forniva implicitamente, va invece
-- dichiarato: tutte le query di questo servizio filtrano per utente.
-- ============================================================================

CREATE TABLE IF NOT EXISTS notifiche (
    id                bigserial PRIMARY KEY,
    utente_id         bigint        NOT NULL,
    titolo            varchar(200)  NOT NULL,
    messaggio         varchar(1000) NOT NULL,
    tipo              varchar(50)   NOT NULL,
    letta             boolean       NOT NULL,
    data_creazione    timestamp     NOT NULL,
    data_lettura      timestamp,
    prenotazione_id   bigint,
    nome_stanza       varchar(100),
    data_prenotazione timestamp,
    admin_nome        varchar(100)
);

CREATE INDEX IF NOT EXISTS idx_notifiche_utente
    ON notifiche (utente_id, data_creazione DESC);

-- Le query sul contatore delle non lette filtrano anche su letta
CREATE INDEX IF NOT EXISTS idx_notifiche_utente_non_lette
    ON notifiche (utente_id)
    WHERE letta = false;
