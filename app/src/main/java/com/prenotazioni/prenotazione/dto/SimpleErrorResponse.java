package com.prenotazioni.prenotazione.dto;

import lombok.Value;

/**
 * Errore a una sola chiave, per i pochi endpoint (es. GET /api/rooms/{id}/details) che
 * oggi rispondono con Collections.singletonMap("error", msg) invece del pieno ApiEnvelope.
 */
@Value
public class SimpleErrorResponse {
    String error;
}
