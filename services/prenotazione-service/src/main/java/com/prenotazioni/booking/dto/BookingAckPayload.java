package com.prenotazioni.booking.dto;

import com.prenotazioni.booking.model.Booking;
import lombok.Value;

/** Risposta di successo per creazione/modifica prenotazione (POST /prenota, PUT /{id}). */
@Value
public class BookingAckPayload {
    Booking prenotazione;
    Long aulaId;
    String periodo;
}
