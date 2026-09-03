package com.prenotazioni.prenotazione.model;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Disponibilita' CALCOLATA di un'aula in un dato istante, cioe' il campo "status"
 * di RoomDetailsResponse. Non e' persistita: viene derivata ogni volta dalle
 * prenotazioni che attraversano quel momento.
 *
 * E' un vocabolario a se' e non un riuso di {@link StatoAula}: quello non ha
 * PRENOTATA, che qui e' il caso piu' frequente. Prima di questo enum erano
 * stringhe crude ripetute in tre copie dello stesso blocco.
 *
 * I valori restano minuscoli e @JsonValue li serializza tali: il frontend legge
 * "libera"/"prenotata"/"bloccata" e cambiarli sarebbe una modifica di contratto.
 * Non serve un AttributeConverter come per gli enum persistiti, perche' questo
 * valore non tocca mai il database.
 */
public enum DisponibilitaAula {

    LIBERA("libera"),
    PRENOTATA("prenotata"),
    BLOCCATA("bloccata");

    private final String valore;

    DisponibilitaAula(String valore) {
        this.valore = valore;
    }

    @JsonValue
    public String getValore() {
        return valore;
    }
}
