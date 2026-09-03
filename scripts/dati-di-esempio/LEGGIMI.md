# Questi file NON sono lo schema del database

Lo schema è gestito da **Flyway**, in `src/main/resources/db/migration/`.

## Perché questa nota

Questi `.sql` sembravano definire lo schema, ma non venivano **mai eseguiti**:

- Spring esegue automaticamente solo `schema.sql` / `data.sql` posti in `src/main/resources`;
- Maven non copia nemmeno i file non-`.java` presenti sotto `src/main/java`, quindi non
  finivano neppure nel jar (verificato: assenti da `target/classes`).

Il risultato era una divergenza silenziosa: lo schema reale era quello generato da
Hibernate con `ddl-auto=update`, **privo dei vincoli descritti qui**. Una verifica sul
database di sviluppo ha trovato **0 CHECK constraint** su 3 attesi, e un `INSERT` con
ruolo `'SUPERUSER'` veniva accettato senza errori.

## Cosa fare adesso

- **Modifiche allo schema**: aggiungere una nuova migrazione in
  `src/main/resources/db/migration/` (`V3__descrizione.sql`, `V4__...`). Le migrazioni
  già applicate non vanno più modificate: Flyway ne verifica il checksum e fallisce.
- **`ddl-auto` è ora `validate`**: Hibernate non modifica più il database, verifica solo
  che le entity corrispondano e fallisce all'avvio se divergono.

## Cosa resta utile qui

Gli `INSERT` di popolamento iniziale (utenti e aule di esempio, con password già in
BCrypt). Sono volutamente **fuori** dalle migrazioni: dati di comodo per lo sviluppo
locale, che non devono finire in automatico in un ambiente di produzione. Vanno eseguiti
a mano quando servono.
