package com.classroom.booking.dto;

import com.classroom.booking.model.Booking;
import lombok.Value;

import java.util.List;

/** The admin view of every booking (cancelled ones included), with per-status statistics. */
@Value
public class AdminBookingsPayload {
    List<Booking> bookings;
    BookingStats stats;
}
