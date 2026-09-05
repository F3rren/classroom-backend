package com.prenotazioni.booking.dto;

import com.prenotazioni.booking.model.Booking;
import lombok.Getter;

import java.util.List;

/** Risposta non avvolta di GET /api/bookings/{id}/details. */
@Getter
public class BookingWithDetailsPayload {
    private final Booking booking;
    private final List<BookingDetailDto> fullDetails;
    private final int totalDetails;

    public BookingWithDetailsPayload(Booking booking, List<BookingDetailDto> fullDetails) {
        this.booking = booking;
        this.fullDetails = fullDetails;
        this.totalDetails = fullDetails.size();
    }
}
