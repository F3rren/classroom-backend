-- ============================================================================
-- V2 - the domain constraints on status and role.
--
-- These CHECKs were documented in files under src/main/java/.../sql/, but those files were
-- NEVER executed: Spring automatically runs only schema.sql and data.sql in resources, and
-- Maven does not even copy .sql files sitting under src/main/java. The real schema was
-- therefore Hibernate's, without any of these constraints: a check against the database
-- confirmed 0 CHECKs present, and an INSERT with the role 'SUPERUSER' was accepted without
-- complaint.
--
-- These are the values the BookingStatus, RoomStatus and Role enums expect, and already
-- enforce on the application side. Here they become a guarantee for anybody writing to the
-- database around the application too: manual scripts, imports, other services.
--
-- Safe on existing data: the values present were checked before this was written, and all
-- of them fall inside the admitted sets.
-- ============================================================================

ALTER TABLE utenti
    ADD CONSTRAINT utente_ruolo_check
    CHECK (ruolo IN ('admin', 'user'));

ALTER TABLE aule
    ADD CONSTRAINT aula_stato_check
    CHECK (stato IN ('libera', 'occupata', 'bloccata', 'manutenzione'));

-- 'confermata' is never assigned by the code but stays admitted: it is historically
-- provided for, and a legacy row carrying it has to remain readable. BookingStatus includes
-- it for the same reason.
ALTER TABLE prenotazioni
    ADD CONSTRAINT prenotazione_stato_check
    CHECK (stato IN ('prenotata', 'confermata', 'bloccata', 'manutenzione', 'annullata'));
