package com.classroom.booking.dto;

import java.util.List;

/** A list of booking details, not wrapped in ApiEnvelope (the shape that was already there). */
public record BookingDetailListPayload(List<BookingDetailDto> bookings, int totalBookings) {

    public BookingDetailListPayload(List<BookingDetailDto> bookings) {
        this(bookings, bookings.size());
    }
}
