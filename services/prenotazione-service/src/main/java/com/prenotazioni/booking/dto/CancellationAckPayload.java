package com.prenotazioni.booking.dto;

import lombok.Value;

/** Risposta di successo per DELETE /api/bookings/{id}. */
@Value
public class CancellationAckPayload {
    Long prenotazioneId;
    Long utenteId;
    String dataAnnullamento;
}
