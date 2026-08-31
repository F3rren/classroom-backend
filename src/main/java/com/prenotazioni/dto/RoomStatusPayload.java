package com.prenotazioni.dto;

import java.time.LocalDateTime;

/** Risposta non avvolta di GET /api/prenotazioni/stato/{aulaId} (comportamento gia' esistente). */
public class RoomStatusPayload {
    private final Long aulaId;
    private final String stato;
    private final LocalDateTime timestamp;

    public RoomStatusPayload(Long aulaId, String stato, LocalDateTime timestamp) {
        this.aulaId = aulaId;
        this.stato = stato;
        this.timestamp = timestamp;
    }

    public Long getAulaId() { return aulaId; }
    public String getStato() { return stato; }
    public LocalDateTime getTimestamp() { return timestamp; }
}
