package com.prenotazioni.dto;

import lombok.Getter;

/** Risposta di GET /api/prenotazioni/disponibilita. */
@Getter
public class AvailabilityPayload {
    private final Long aulaId;
    private final boolean disponibile;
    private final String periodo;
    private final String status;

    public AvailabilityPayload(Long aulaId, boolean disponibile, String periodo) {
        this.aulaId = aulaId;
        this.disponibile = disponibile;
        this.periodo = periodo;
        this.status = disponibile ? "LIBERA" : "OCCUPATA";
    }
}
