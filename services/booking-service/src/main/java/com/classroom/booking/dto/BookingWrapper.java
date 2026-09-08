package com.classroom.booking.dto;

import com.classroom.booking.model.Booking;
import lombok.Value;

/** A wrapper not enclosed in ApiEnvelope for GET /api/bookings/{id} (the existing shape). */
@Value
public class BookingWrapper {
    Booking booking;
}
