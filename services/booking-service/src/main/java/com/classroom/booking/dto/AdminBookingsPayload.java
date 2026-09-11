package com.classroom.booking.dto;

import com.classroom.booking.model.Booking;

import java.util.List;

/** The admin view of every booking (cancelled ones included), with per-status statistics. */
public record AdminBookingsPayload(List<Booking> bookings, BookingStats stats) {
}
