-- ============================================================================
-- V1 - the baseline schema.
--
-- It reproduces the schema Hibernate used to create with ddl-auto=update. On databases that
-- ALREADY exist this migration is NOT executed: spring.flyway.baseline-on-migrate=true with
-- baseline-version=1 marks it as already applied. It runs only on new databases, which
-- therefore get exactly the same structure instead of depending on ddl-auto.
--
-- The domain constraints (the CHECKs on status and role) are NOT here but in V2, precisely
-- because they have to be applied to existing databases too, where they are missing.
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

-- The protection against concurrent double booking.
-- The application check isRoomAvailable() is not enough: between the check and the save,
-- another transaction can insert an overlapping booking. This constraint refuses it at the
-- database level; the controllers translate the resulting DataIntegrityViolationException
-- into a 409 BookingConflictException.
-- Cancelled bookings are excluded: they no longer hold the room.
CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE prenotazioni
    ADD CONSTRAINT prenotazioni_no_overlap
    EXCLUDE USING gist (
        aula_id WITH =,
        tsrange(inizio, fine) WITH &&
    ) WHERE (stato <> 'annullata');
