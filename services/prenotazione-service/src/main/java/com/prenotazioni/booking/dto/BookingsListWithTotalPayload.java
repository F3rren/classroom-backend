package com.prenotazioni.booking.dto;

import com.prenotazioni.booking.model.Booking;
import lombok.Getter;

import java.util.List;

/** Risposta non avvolta di GET /api/prenotazioni/future: { prenotazioni, totalPrenotazioni }. */
@Getter
public class BookingsListWithTotalPayload {
    private final List<Booking> prenotazioni;
    private final int totalPrenotazioni;

    public BookingsListWithTotalPayload(List<Booking> prenotazioni) {
        this.prenotazioni = prenotazioni;
        this.totalPrenotazioni = prenotazioni.size();
    }
}
