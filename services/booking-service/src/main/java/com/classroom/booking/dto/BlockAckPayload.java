package com.classroom.booking.dto;

import com.classroom.booking.model.Booking;
import lombok.Value;

/** The success response when an admin blocks a room (POST /block). */
@Value
public class BlockAckPayload {
    Booking block;
    Long roomId;
    String period;
    Long adminId;
}
