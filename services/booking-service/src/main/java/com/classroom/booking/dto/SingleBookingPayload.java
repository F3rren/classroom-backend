package com.classroom.booking.dto;

import com.classroom.booking.model.Booking;

import java.util.List;

/**
 * A booking list carrying only the "bookings" key (no "totalBookings"), reused by
 * GET /mine and by the non-empty branch of the base GET - the shape that was already there.
 */
public record SingleBookingPayload(List<Booking> bookings) {
}
