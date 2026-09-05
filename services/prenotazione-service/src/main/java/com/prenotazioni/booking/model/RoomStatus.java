package com.prenotazioni.booking.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Locale;

/**
 * Stato PERSISTITO di un'aula, cioe' il campo aula.stato salvato a database.
 *
 * Da non confondere con i due vocabolari MAIUSCOLI calcolati a runtime, che sono
 * contratti diversi e restano stringhe:
 *  - PrenotazioneService.getStatoAula() restituisce LIBERA/MANUTENZIONE/BLOCCATA/PRENOTATA
 *    (nota: PRENOTATA, che qui non esiste, e nessun OCCUPATA);
 *  - AvailabilityPayload.status restituisce LIBERA/OCCUPATA.
 * Il frontend legge anche quei valori maiuscoli, quindi unificarli sarebbe una
 * modifica di contratto, non un refactor.
 *
 * C'e' infine {@link DisponibilitaAula}, il campo "status" di RoomDetailsResponse:
 * minuscolo come questo enum ma con PRENOTATA e senza OCCUPATA, quindi nemmeno
 * quello e' sostituibile con StatoAula.
 *
 * Come per StatoPrenotazione, il valore su disco e nel JSON resta minuscolo:
 * lo impone il CHECK constraint aula_stato_check e il bundle compilato del frontend.
 */
public enum RoomStatus {

    LIBERA("libera"),
    OCCUPATA("occupata"),
    BLOCCATA("bloccata"),
    MANUTENZIONE("manutenzione");

    private final String valore;

    RoomStatus(String valore) {
        this.valore = valore;
    }

    @JsonValue
    public String getValore() {
        return valore;
    }

    @JsonCreator
    public static RoomStatus da(String valore) {
        if (valore == null) {
            return null;
        }
        String normalizzato = valore.trim().toLowerCase(Locale.ROOT);
        for (RoomStatus stato : values()) {
            if (stato.valore.equals(normalizzato)) {
                return stato;
            }
        }
        throw new IllegalArgumentException("Stato aula non valido: " + valore);
    }

    @Converter(autoApply = true)
    public static class JpaConverter implements AttributeConverter<RoomStatus, String> {

        @Override
        public String convertToDatabaseColumn(RoomStatus stato) {
            return stato == null ? null : stato.getValore();
        }

        @Override
        public RoomStatus convertToEntityAttribute(String valore) {
            return da(valore);
        }
    }
}
