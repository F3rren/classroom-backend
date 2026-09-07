package com.prenotazioni.booking.dto;

import com.prenotazioni.booking.model.Booking;
import lombok.Value;

import java.util.List;

/**
 * A booking list carrying only the "bookings" key (no "totalBookings"), reused by
 * GET /mine and by the non-empty branch of the base GET - the shape that was already there.
 */
@Value
public class SingleBookingPayload {
    List<Booking> bookings;
}
