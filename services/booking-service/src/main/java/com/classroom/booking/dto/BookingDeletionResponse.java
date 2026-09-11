package com.classroom.booking.dto;

/** Confirmation that an admin deleted a booking. */
public record BookingDeletionResponse(Long deletedBookingId, Long adminId, boolean adminAction, String reason) {

    public BookingDeletionResponse(Long deletedBookingId, Long adminId, String reason) {
        this(deletedBookingId, adminId, true, reason);
    }
}
