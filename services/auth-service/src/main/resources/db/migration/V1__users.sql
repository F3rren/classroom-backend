-- ============================================================================
-- The user service's schema: a single table.
--
-- It is the same definition that lived in the monolith's V1, brought here together with the
-- CHECK on the role that lived in V2. It is not a copy to be kept in step: from now on this
-- is the only definition, and the users table disappears from the
-- schema di booking-service.
--
-- Bookings and notifications keep a user id with no foreign key, because they live in other
-- databases. No FK can cross that boundary.
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

-- Keeps the role's domain in step with the Role enum. RoleTest loops over the enum's values
-- precisely to catch a constant added without a matching migration.
ALTER TABLE utenti
    DROP CONSTRAINT IF EXISTS utente_ruolo_check;

ALTER TABLE utenti
    ADD CONSTRAINT utente_ruolo_check
    CHECK (ruolo IN ('admin', 'user'));
