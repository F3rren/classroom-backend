package com.prenotazioni.booking.dto;

import com.prenotazioni.booking.model.Booking;
import lombok.Getter;

import java.util.List;

/** Risposta non avvolta di GET /api/prenotazioni/stato/{stato}. */
@Getter
public class BookingsByStatusPayload {
    private final String stato;
    private final List<Booking> prenotazioni;
    private final int totalPrenotazioni;

    public BookingsByStatusPayload(String stato, List<Booking> prenotazioni) {
        this.stato = stato;
        this.prenotazioni = prenotazioni;
        this.totalPrenotazioni = prenotazioni.size();
    }
}
