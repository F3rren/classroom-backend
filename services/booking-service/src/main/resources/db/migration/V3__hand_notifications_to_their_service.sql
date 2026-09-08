-- ============================================================================
-- Notifications move to notification-service, which owns its own table in its own database.
--
-- Why a new migration and not a change to V1: V1 has already been applied, and Flyway checks
-- its checksum. Changing it would fail startup on any database it has already run against,
-- with a validation error. Migrations already applied are history: you correct them by going
-- forward, not by rewriting.
--
-- CAREFUL, DATA LOSS: this DROP removes the existing notifications. On a database holding
-- real data it has to be preceded by a data migration into notification-service's database.
-- On the development database, empty today, there is nothing to save.
-- ============================================================================

DROP TABLE IF EXISTS notifiche;
