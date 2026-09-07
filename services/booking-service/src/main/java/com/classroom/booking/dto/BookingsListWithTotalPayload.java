package com.classroom.booking.dto;

import com.classroom.booking.model.Booking;
import lombok.Getter;

import java.util.List;

/** The unwrapped response of GET /api/bookings/future: { bookings, totalBookings }. */
@Getter
public class BookingsListWithTotalPayload {
    private final List<Booking> bookings;
    private final int totalBookings;

    public BookingsListWithTotalPayload(List<Booking> bookings) {
        this.bookings = bookings;
        this.totalBookings = bookings.size();
    }
}
