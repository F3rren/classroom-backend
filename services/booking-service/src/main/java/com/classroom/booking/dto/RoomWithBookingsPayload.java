package com.classroom.booking.dto;

import com.classroom.booking.model.Room;

import java.util.List;

/** A room plus its detailed bookings, not wrapped in ApiEnvelope (the existing shape). */
public record RoomWithBookingsPayload(Room room, List<BookingDetailDto> bookings, int totalBookings) {

    public RoomWithBookingsPayload(Room room, List<BookingDetailDto> bookings) {
        this(room, bookings, bookings.size());
    }
}
