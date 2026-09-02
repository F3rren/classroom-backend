-- ============================================================================
-- Le notifiche passano a notifica-service, che possiede la propria tabella nel
-- proprio database.
--
-- Perche' una migrazione nuova e non una modifica di V1: V1 e' gia' stata applicata,
-- e Flyway ne verifica il checksum. Cambiarla farebbe fallire l'avvio su qualunque
-- database in cui e' gia' passata, con un errore di validazione. Le migrazioni gia'
-- applicate sono storia: si corregge andando avanti, non riscrivendo.
--
-- ATTENZIONE, PERDITA DI DATI: questo DROP elimina le notifiche esistenti. Su un
-- database con dati veri va preceduto da una migrazione dei dati verso il database
-- di notifica-service. Sul database di sviluppo, oggi vuoto, non c'e' nulla da salvare.
-- ============================================================================

DROP TABLE IF EXISTS notifiche;
