-- ============================================================================
-- The notification service's schema.
--
-- Extracted from the monolith's V1__baseline_schema.sql, with one difference worth
-- understanding rather than merely suffering: utente_id no longer has REFERENCES utenti (id).
--
-- The users table belongs to another service and will live in another database, so the
-- foreign key is not expressible. The database used to guarantee that no notification could
-- exist for a user who does not; that guarantee is now the application's, and it is one of
-- the real costs of the split.
--
-- The index on utente_id, which the foreign key supplied implicitly, does have to be
-- declared: every query in this service filters by user.
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

-- The unread-count queries filter on the read flag as well
CREATE INDEX IF NOT EXISTS idx_notifiche_utente_non_lette
    ON notifiche (utente_id)
    WHERE letta = false;
