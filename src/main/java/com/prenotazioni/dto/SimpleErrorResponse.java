package com.prenotazioni.dto;

/**
 * Errore a una sola chiave, per i pochi endpoint (es. GET /api/rooms/{id}/details) che
 * oggi rispondono con Collections.singletonMap("error", msg) invece del pieno ApiEnvelope.
 */
public class SimpleErrorResponse {
    private final String error;

    public SimpleErrorResponse(String error) {
        this.error = error;
    }

    public String getError() { return error; }
}
