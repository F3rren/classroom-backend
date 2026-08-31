package com.prenotazioni.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Errore a una sola chiave, per i pochi endpoint (es. GET /api/rooms/{id}/details) che
 * oggi rispondono con Collections.singletonMap("error", msg) invece del pieno ApiEnvelope.
 */
@Getter
@AllArgsConstructor
public class SimpleErrorResponse {
    private final String error;
}
