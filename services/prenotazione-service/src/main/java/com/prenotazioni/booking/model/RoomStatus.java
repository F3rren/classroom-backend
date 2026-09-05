package com.prenotazioni.booking.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Locale;

/**
 * Stato PERSISTITO di un'aula, cioe' il campo aula.status salvato a database.
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

    private final String value;

    RoomStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static RoomStatus da(String value) {
        if (value == null) {
            return null;
        }
        String normalizzato = value.trim().toLowerCase(Locale.ROOT);
        for (RoomStatus status : values()) {
            if (status.value.equals(normalizzato)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Stato aula non valido: " + value);
    }

    @Converter(autoApply = true)
    public static class JpaConverter implements AttributeConverter<RoomStatus, String> {

        @Override
        public String convertToDatabaseColumn(RoomStatus status) {
            return status == null ? null : status.getValue();
        }

        @Override
        public RoomStatus convertToEntityAttribute(String value) {
            return da(value);
        }
    }
}
