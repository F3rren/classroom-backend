package com.classroom.booking.dto;

import com.classroom.booking.model.Booking;

import java.util.List;

/** The unwrapped response of GET /api/bookings/future: { bookings, totalBookings }. */
public record BookingsListWithTotalPayload(List<Booking> bookings, int totalBookings) {

    public BookingsListWithTotalPayload(List<Booking> bookings) {
        this(bookings, bookings.size());
    }
}
