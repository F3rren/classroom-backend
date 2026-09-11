package com.classroom.booking.dto;

import com.classroom.booking.model.Booking;

/** The success response when an admin blocks a room (POST /block). */
public record BlockAckPayload(Booking block, Long roomId, String period, Long adminId) {
}
