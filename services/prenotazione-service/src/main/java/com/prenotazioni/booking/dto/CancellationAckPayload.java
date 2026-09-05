package com.prenotazioni.booking.dto;

import lombok.Value;

/** Risposta di successo per DELETE /api/bookings/{id}. */
@Value
public class CancellationAckPayload {
    Long bookingId;
    Long userId;
    String dataAnnullamento;
}
