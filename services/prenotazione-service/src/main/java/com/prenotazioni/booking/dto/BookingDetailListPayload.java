package com.prenotazioni.booking.dto;

import lombok.Getter;

import java.util.List;

/** Lista di dettagli prenotazione, non avvolta nell'envelope ApiEnvelope (comportamento gia' esistente). */
@Getter
public class BookingDetailListPayload {
    private final List<BookingDetailDto> bookings;
    private final int totalBookings;

    public BookingDetailListPayload(List<BookingDetailDto> bookings) {
        this.bookings = bookings;
        this.totalBookings = bookings.size();
    }
}
