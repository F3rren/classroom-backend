package com.prenotazioni.booking.dto;

import com.prenotazioni.booking.model.Booking;
import lombok.Getter;

import java.util.List;

/** Risposta non avvolta di GET /api/bookings/future: { prenotazioni, totalPrenotazioni }. */
@Getter
public class BookingsListWithTotalPayload {
    private final List<Booking> bookings;
    private final int totalBookings;

    public BookingsListWithTotalPayload(List<Booking> bookings) {
        this.bookings = bookings;
        this.totalBookings = bookings.size();
    }
}
