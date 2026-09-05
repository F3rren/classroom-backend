package com.prenotazioni.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/** Risposta di GET /api/bookings/availability. */
@Getter
@Schema(description = "Esito della verifica di disponibilita' di un'aula in un periodo")
public class AvailabilityPayload {
    @Schema(description = "Aula verificata", example = "3")
    private final Long roomId;
    @Schema(description = "true se l'aula e' libera nel periodo richiesto", example = "true")
    private final boolean available;
    @Schema(description = "Periodo verificato", example = "2026-12-25 14:30:00 - 2026-12-25 16:30:00")
    private final String period;
    @Schema(description = "Esito in forma testuale. Vocabolario MAIUSCOLO, distinto dallo stato persistito dell'aula",
            allowableValues = {"FREE", "BUSY"}, example = "FREE")
    private final String status;

    public AvailabilityPayload(Long roomId, boolean available, String period) {
        this.roomId = roomId;
        this.available = available;
        this.period = period;
        this.status = available ? "FREE" : "BUSY";
    }
}
