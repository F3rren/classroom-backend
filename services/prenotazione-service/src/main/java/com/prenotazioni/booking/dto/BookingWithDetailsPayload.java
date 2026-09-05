package com.prenotazioni.booking.dto;

import com.prenotazioni.booking.model.Booking;
import lombok.Getter;

import java.util.List;

/** Risposta non avvolta di GET /api/prenotazioni/{id}/details. */
@Getter
public class BookingWithDetailsPayload {
    private final Booking prenotazione;
    private final List<BookingDetailDto> dettagliCompleti;
    private final int totalDettagli;

    public BookingWithDetailsPayload(Booking prenotazione, List<BookingDetailDto> dettagliCompleti) {
        this.prenotazione = prenotazione;
        this.dettagliCompleti = dettagliCompleti;
        this.totalDettagli = dettagliCompleti.size();
    }
}
