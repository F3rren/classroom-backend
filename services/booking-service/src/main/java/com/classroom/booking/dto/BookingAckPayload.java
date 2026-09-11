package com.classroom.booking.dto;

import com.classroom.booking.model.Booking;

/** The success response for creating or updating a booking (POST /book, PUT /{id}). */
public record BookingAckPayload(Booking booking, Long roomId, String period) {
}
