package com.prenotazioni.booking.dto;

import lombok.Getter;

import java.util.List;

/** Lista di dettagli prenotazione, non avvolta nell'envelope ApiEnvelope (comportamento gia' esistente). */
@Getter
public class BookingDetailListPayload {
    private final List<BookingDetailDto> prenotazioni;
    private final int totalPrenotazioni;

    public BookingDetailListPayload(List<BookingDetailDto> prenotazioni) {
        this.prenotazioni = prenotazioni;
        this.totalPrenotazioni = prenotazioni.size();
    }
}
