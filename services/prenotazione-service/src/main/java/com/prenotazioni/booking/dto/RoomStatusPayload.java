package com.prenotazioni.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Value;

import java.time.LocalDateTime;

/** The unwrapped response of GET /api/bookings/room-status/{roomId} (the existing shape). */
@Value
@Schema(description = "How occupied a room is at this moment")
public class RoomStatusPayload {
    @Schema(description = "The room that was asked about", example = "3")
    Long roomId;
    @Schema(description = "The status computed now. An UPPERCASE vocabulary, different from the stored room.status: "
            + "include BOOKED e non ha BUSY",
            allowableValues = {"FREE", "BOOKED", "BLOCKED", "MAINTENANCE"}, example = "FREE")
    String status;
    @Schema(description = "The moment the status refers to")
    LocalDateTime timestamp;
}
