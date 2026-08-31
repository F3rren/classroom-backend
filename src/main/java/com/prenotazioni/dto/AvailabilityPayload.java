package com.prenotazioni.dto;

/** Risposta di GET /api/prenotazioni/disponibilita. */
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

    public Long getAulaId() { return aulaId; }
    public boolean isDisponibile() { return disponibile; }
    public String getPeriodo() { return periodo; }
    public String getStatus() { return status; }
}
