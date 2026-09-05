package com.prenotazioni.booking.dto;

import com.prenotazioni.booking.model.Booking;
import lombok.Getter;

import java.util.List;

/** Risposta non avvolta di GET /api/bookings/status/{stato}. */
@Getter
public class BookingsByStatusPayload {
    private final String status;
    private final List<Booking> bookings;
    private final int totalBookings;

    public BookingsByStatusPayload(String status, List<Booking> bookings) {
        this.status = status;
        this.bookings = bookings;
        this.totalBookings = bookings.size();
    }
}
