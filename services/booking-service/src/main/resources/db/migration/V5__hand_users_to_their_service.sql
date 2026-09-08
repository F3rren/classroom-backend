-- ============================================================================
-- The users table moves to auth-service.
--
-- V4 had already dropped the foreign key and copied the username and name onto the bookings,
-- so nothing still needed is lost here: the bookings keep utente_id, utente_username and
-- utente_nome as a snapshot. (V6 renames those columns to user_id, user_username, user_name.)
--
-- From this point on the only definition of the users table is auth-service's V1. Keeping a
-- copy here would mean two schemas to hold in step by hand, which is exactly what the split
-- is meant to avoid.
--
-- The data need not be lost: before applying this migration to a populated database, move
-- the users across into auth-service's database, for instance with
--     pg_dump -t utenti classroom | psql classroom_users
-- On an empty database there is nothing to move.
-- ============================================================================

DROP TABLE IF EXISTS utenti;
