package com.classroom.booking.dto;

import com.classroom.booking.model.Booking;
import lombok.Getter;

import java.util.List;

/** The unwrapped response of GET /api/bookings/status/{status}. */
@Getter
public class BookingsByStatusPayload {
    private final String status;
    private final List<Booking> bookings;
    private final int totalBookings;

    public BookingsByStatusPayload(String status, List<Booking> bookings) {
        this.status = status;
        this.bookings = bookings;
        this.totalBookings = bookings.size();
    }
}
