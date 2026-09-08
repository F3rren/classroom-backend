package com.classroom.booking.dto;

import lombok.Getter;

import java.util.List;

/** A list of booking details, not wrapped in ApiEnvelope (the shape that was already there). */
@Getter
public class BookingDetailListPayload {
    private final List<BookingDetailDto> bookings;
    private final int totalBookings;

    public BookingDetailListPayload(List<BookingDetailDto> bookings) {
        this.bookings = bookings;
        this.totalBookings = bookings.size();
    }
}
