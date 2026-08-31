package com.prenotazioni.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Locale;

/**
 * Stato di una prenotazione, prima rappresentato da stringhe libere sparse in una
 * sessantina di punti fra service, controller e query.
 *
 * IMPORTANTE - il formato esterno resta minuscolo, in entrambe le direzioni:
 *  - verso il frontend: il bundle compilato confronta le stringhe minuscole esatte
 *    ("annullata", "bloccata", "prenotata"), quindi @JsonValue serializza il valore
 *    minuscolo e NON il nome della costante;
 *  - verso il database: la colonna ha un CHECK constraint che ammette solo quei valori
 *    minuscoli (prenotazione_stato_check), quindi la persistenza passa dal converter
 *    qui sotto invece che da @Enumerated, che salverebbe il nome maiuscolo.
 * Cosi' il tipo diventa sicuro nel codice Java senza cambiare nulla sul filo ne' su disco.
 */
public enum StatoPrenotazione {

    PRENOTATA("prenotata"),
    /**
     * Ammesso dal CHECK constraint e citato storicamente, ma nessun punto del codice lo
     * assegna: e' tenuto solo perche' una riga legacy con questo valore deve poter essere
     * letta senza far fallire la deserializzazione.
     */
    CONFERMATA("confermata"),
    BLOCCATA("bloccata"),
    MANUTENZIONE("manutenzione"),
    ANNULLATA("annullata");

    private final String valore;

    StatoPrenotazione(String valore) {
        this.valore = valore;
    }

    /** Valore usato nel JSON e nel database. */
    @JsonValue
    public String getValore() {
        return valore;
    }

    /**
     * Accetta qualunque combinazione di maiuscole/minuscole: prima della migrazione il
     * codice confrontava con equalsIgnoreCase, quindi dati o richieste con case diverso
     * devono continuare a essere accettati.
     */
    @JsonCreator
    public static StatoPrenotazione da(String valore) {
        if (valore == null) {
            return null;
        }
        String normalizzato = valore.trim().toLowerCase(Locale.ROOT);
        for (StatoPrenotazione stato : values()) {
            if (stato.valore.equals(normalizzato)) {
                return stato;
            }
        }
        throw new IllegalArgumentException("Stato prenotazione non valido: " + valore);
    }

    /** True se la prenotazione e' attiva, cioe' annullabile dall'utente. */
    public boolean isAttiva() {
        return this == PRENOTATA;
    }

    /** True per gli stati che occupano l'aula per volonta' di un admin. */
    public boolean isInterventoAdmin() {
        return this == BLOCCATA || this == MANUTENZIONE;
    }

    /**
     * Converter JPA: scrive/legge il valore minuscolo, non il nome della costante.
     * autoApply cosi' non serve annotare ogni campo.
     */
    @Converter(autoApply = true)
    public static class JpaConverter implements AttributeConverter<StatoPrenotazione, String> {

        @Override
        public String convertToDatabaseColumn(StatoPrenotazione stato) {
            return stato == null ? null : stato.getValore();
        }

        @Override
        public StatoPrenotazione convertToEntityAttribute(String valore) {
            return da(valore);
        }
    }
}
