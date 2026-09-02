-- ============================================================================
-- La prenotazione smette di puntare alla tabella utenti.
--
-- Gli utenti stanno per passare ad auth-service, con un database proprio: una
-- chiave esterna verso una tabella che non sara' piu' qui non puo' sopravvivere.
-- Al suo posto le prenotazioni conservano un'ISTANTANEA di chi ha prenotato, con
-- gli stessi tre campi che l'API gia' esponeva (id, username, nome).
--
-- ATTENZIONE, e' una perdita reale di garanzia: finche' la FK esiste il database
-- impedisce prenotazioni orfane. Dopo questa migrazione nulla lo impedisce piu',
-- e la cancellazione di un utente diventa responsabilita' applicativa. E' il
-- prezzo della separazione, non un effetto collaterale da scoprire dopo.
--
-- L'istantanea e' voluta: mostra chi ha prenotato COME ERA ALLORA. Un utente che
-- cambia nome non riscrive lo storico, e questi campi non vanno risincronizzati.
-- ============================================================================

ALTER TABLE prenotazioni ADD COLUMN IF NOT EXISTS utente_username varchar(50);
ALTER TABLE prenotazioni ADD COLUMN IF NOT EXISTS utente_nome     varchar(100);

-- Riempimento finche' le due tabelle sono ancora nello stesso database: dopo la
-- separazione questa join non sarebbe piu' scrivibile.
UPDATE prenotazioni p
   SET utente_username = u.username,
       utente_nome     = u.nome
  FROM utenti u
 WHERE u.id = p.utente_id
   AND (p.utente_username IS NULL OR p.utente_nome IS NULL);

-- La colonna utente_id resta: cambia solo il fatto che non e' piu' una chiave
-- esterna. Il nome del vincolo e' generato da PostgreSQL, quindi va cercato nel
-- catalogo invece di essere indovinato.
DO $$
DECLARE
    nome_vincolo text;
BEGIN
    SELECT con.conname INTO nome_vincolo
      FROM pg_constraint con
      JOIN pg_class rel ON rel.oid = con.conrelid
     WHERE rel.relname = 'prenotazioni'
       AND con.contype = 'f'
       AND con.confrelid = 'utenti'::regclass;

    IF nome_vincolo IS NOT NULL THEN
        EXECUTE format('ALTER TABLE prenotazioni DROP CONSTRAINT %I', nome_vincolo);
    END IF;
END $$;
