-- ============================================================================
-- The status values themselves move to English.
--
-- V6 and V7 moved every NAME in this schema. This moves the last Italian left
-- in it: the CONTENT of the two status columns, and the CHECK constraints that
-- spell those values out.
--
-- WHY IT IS SAFE TO DO NOW, AND WOULD NOT HAVE BEEN BEFORE: these strings are
-- also what the API puts on the wire, and the code used to say in as many words
-- that the compiled frontend bundle compared them exactly. That bundle is no
-- longer in this repository, and every endpoint and JSON key around it has
-- already been renamed - /api/prenotazioni is now /api/bookings, "inizio" is
-- now "startTime". A client built against the old values cannot talk to this
-- API any more for a dozen other reasons, so keeping 'prenotata' would protect
-- nothing while leaving the database half translated.
--
-- ORDER MATTERS HERE. The two CHECK constraints and the exclusion constraint
-- all name the old values, so nothing can be updated while they still stand:
-- drop them, rewrite the data, then put them back reading the new values.
-- Flyway runs the whole file in one transaction, so there is no window where
-- the table sits unprotected.
-- ============================================================================

-- 1. Take down what spells out the old values.
--
-- bookings_no_overlap has to go too, and it is the one worth being careful
-- about: its WHERE clause excludes cancelled bookings, so the value is baked
-- into its definition and there is no ALTER that edits it in place. Guarded by
-- IF EXISTS for the same reason as V7 - on a database that predates Flyway
-- these names were never guaranteed.
ALTER TABLE bookings DROP CONSTRAINT IF EXISTS bookings_no_overlap;
ALTER TABLE bookings DROP CONSTRAINT IF EXISTS booking_status_check;
ALTER TABLE rooms    DROP CONSTRAINT IF EXISTS room_status_check;

-- 2. Rewrite the data.
--
-- 'bloccata' and 'manutenzione' exist in both columns and translate the same
-- way in each, so the two statements do not have to agree on anything.
UPDATE bookings SET status = CASE status
    WHEN 'prenotata'   THEN 'booked'
    WHEN 'confermata'  THEN 'confirmed'
    WHEN 'bloccata'    THEN 'blocked'
    WHEN 'manutenzione' THEN 'maintenance'
    WHEN 'annullata'   THEN 'cancelled'
    ELSE status
END;

UPDATE rooms SET status = CASE status
    WHEN 'libera'      THEN 'free'
    WHEN 'occupata'    THEN 'busy'
    WHEN 'bloccata'    THEN 'blocked'
    WHEN 'manutenzione' THEN 'maintenance'
    ELSE status
END;

-- 3. Put the constraints back, now reading English.
--
-- 'confirmed' is never assigned by the code but stays allowed, exactly as
-- 'confermata' did in V2: dropping it from the CHECK would turn old rows into
-- rows the database refuses to accept back.
ALTER TABLE rooms
    ADD CONSTRAINT room_status_check
    CHECK (status IN ('free', 'busy', 'blocked', 'maintenance'));

ALTER TABLE bookings
    ADD CONSTRAINT booking_status_check
    CHECK (status IN ('booked', 'confirmed', 'blocked', 'maintenance', 'cancelled'));

-- The protection against concurrent double booking, rebuilt on the new value.
-- Same definition as V1 apart from the names: overlapping time ranges on the
-- same room are refused by the database, because the application check between
-- read and write leaves a gap another transaction can slip through. Cancelled
-- bookings are excluded - they no longer hold the room.
ALTER TABLE bookings
    ADD CONSTRAINT bookings_no_overlap
    EXCLUDE USING gist (
        room_id WITH =,
        tsrange(start_time, end_time) WITH &&
    ) WHERE (status <> 'cancelled');
