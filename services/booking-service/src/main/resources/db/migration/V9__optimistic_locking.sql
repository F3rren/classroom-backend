-- Optimistic locking on the two entities an admin edits.
--
-- Both PUT /api/admin/rooms/{id} and PUT /api/bookings/{id} are read-modify-write across two
-- transactions: load, change some fields, save. Two people editing the same row therefore
-- both succeeded, and the later write silently replaced the earlier one - no error, no trace,
-- and the first editor's change simply gone. This column is what lets Hibernate notice.
--
-- DEFAULT 0 NOT NULL so existing rows are valid immediately: Hibernate refuses to update a
-- row whose version is null, which would have made every row written before this migration
-- unmodifiable.

ALTER TABLE rooms ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
