package com.classroom.booking.dto;

import com.classroom.booking.model.Booking;

import java.util.List;

/** The unwrapped response of GET /api/bookings/{id}/details. */
public record BookingWithDetailsPayload(Booking booking, List<BookingDetailDto> fullDetails, int totalDetails) {

    public BookingWithDetailsPayload(Booking booking, List<BookingDetailDto> fullDetails) {
        this(booking, fullDetails, fullDetails.size());
    }
}
