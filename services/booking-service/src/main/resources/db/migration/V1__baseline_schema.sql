-- ============================================================================
-- V1 - Schema di base.
--
-- Riproduce lo schema che finora veniva creato da Hibernate con ddl-auto=update.
-- Sui database GIA' esistenti questa migrazione NON viene eseguita: la property
-- spring.flyway.baseline-on-migrate=true con baseline-version=1 la marca come
-- gia' applicata. Viene eseguita solo sui database nuovi, che cosi' ottengono
-- esattamente la stessa struttura invece di dipendere da ddl-auto.
--
-- I vincoli di dominio (CHECK su stato e ruolo) NON stanno qui ma in V2, proprio
-- perche' devono essere applicati anche ai database esistenti, dove mancano.
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

CREATE TABLE IF NOT EXISTS aule (
    id          bigserial PRIMARY KEY,
    nome        varchar(100) NOT NULL UNIQUE,
    capienza    integer      NOT NULL,
    piano       integer      NOT NULL,
    is_virtual  boolean      NOT NULL,
    descrizione text,
    stato       varchar(20)
);

CREATE TABLE IF NOT EXISTS corsi (
    id          bigserial PRIMARY KEY,
    nome        varchar(100) NOT NULL,
    docente     varchar(100) NOT NULL,
    descrizione text
);

CREATE TABLE IF NOT EXISTS prenotazioni (
    id             bigserial PRIMARY KEY,
    aula_id        bigint      NOT NULL REFERENCES aule (id),
    corso_id       bigint      REFERENCES corsi (id),
    utente_id      bigint      NOT NULL REFERENCES utenti (id),
    inizio         timestamp   NOT NULL,
    fine           timestamp   NOT NULL,
    stato          varchar(20) NOT NULL,
    descrizione    text,
    data_creazione timestamp   NOT NULL
);

CREATE TABLE IF NOT EXISTS notifiche (
    id                bigserial PRIMARY KEY,
    utente_id         bigint        NOT NULL REFERENCES utenti (id),
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

-- Protezione contro la doppia prenotazione concorrente.
-- Il controllo applicativo isAulaDisponibile() non basta: fra la verifica e il
-- salvataggio un'altra transazione puo' inserire una prenotazione sovrapposta.
-- Questo vincolo la rifiuta a livello di database; i controller traducono la
-- DataIntegrityViolationException risultante in un 409 BookingConflictException.
-- Le prenotazioni annullate sono escluse: non occupano piu' l'aula.
CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE prenotazioni
    ADD CONSTRAINT prenotazioni_no_overlap
    EXCLUDE USING gist (
        aula_id WITH =,
        tsrange(inizio, fine) WITH &&
    ) WHERE (stato <> 'annullata');
