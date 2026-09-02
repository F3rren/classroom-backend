package com.prenotazioni.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/** Risposta di GET /api/prenotazioni/disponibilita. */
@Getter
@Schema(description = "Esito della verifica di disponibilita' di un'aula in un periodo")
public class AvailabilityPayload {
    @Schema(description = "Aula verificata", example = "3")
    private final Long aulaId;
    @Schema(description = "true se l'aula e' libera nel periodo richiesto", example = "true")
    private final boolean disponibile;
    @Schema(description = "Periodo verificato", example = "2026-12-25 14:30:00 - 2026-12-25 16:30:00")
    private final String periodo;
    @Schema(description = "Esito in forma testuale. Vocabolario MAIUSCOLO, distinto dallo stato persistito dell'aula",
            allowableValues = {"LIBERA", "OCCUPATA"}, example = "LIBERA")
    private final String status;

    public AvailabilityPayload(Long aulaId, boolean disponibile, String periodo) {
        this.aulaId = aulaId;
        this.disponibile = disponibile;
        this.periodo = periodo;
        this.status = disponibile ? "LIBERA" : "OCCUPATA";
    }
}
