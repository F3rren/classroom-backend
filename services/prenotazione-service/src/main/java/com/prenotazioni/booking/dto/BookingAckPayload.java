package com.prenotazioni.booking.dto;

import com.prenotazioni.booking.model.Booking;
import lombok.Value;

/** The success response for creating or updating a booking (POST /book, PUT /{id}). */
@Value
public class BookingAckPayload {
    Booking booking;
    Long roomId;
    String period;
}
