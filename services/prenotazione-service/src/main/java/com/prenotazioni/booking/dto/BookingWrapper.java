package com.prenotazioni.booking.dto;

import com.prenotazioni.booking.model.Booking;
import lombok.Value;

/** A wrapper not enclosed in ApiEnvelope for GET /api/bookings/{id} (the existing shape). */
@Value
public class BookingWrapper {
    Booking booking;
}
