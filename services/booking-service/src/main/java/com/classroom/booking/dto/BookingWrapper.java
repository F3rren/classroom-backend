package com.classroom.booking.dto;

import com.classroom.booking.model.Booking;

/** A wrapper not enclosed in ApiEnvelope for GET /api/bookings/{id} (the existing shape). */
public record BookingWrapper(Booking booking) {
}
