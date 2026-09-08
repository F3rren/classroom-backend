package com.classroom.booking.dto;

import com.classroom.booking.model.Booking;
import lombok.Value;

/** The success response for creating or updating a booking (POST /book, PUT /{id}). */
@Value
public class BookingAckPayload {
    Booking booking;
    Long roomId;
    String period;
}
