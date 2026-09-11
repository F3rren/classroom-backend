package com.classroom.booking.dto;

import com.classroom.booking.model.Booking;

import java.util.List;

/** The unwrapped response of GET /api/bookings/status/{status}. */
public record BookingsByStatusPayload(String status, List<Booking> bookings, int totalBookings) {

    public BookingsByStatusPayload(String status, List<Booking> bookings) {
        this(status, bookings, bookings.size());
    }
}
