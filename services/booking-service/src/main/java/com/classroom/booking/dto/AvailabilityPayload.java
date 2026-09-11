package com.classroom.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Risposta di GET /api/bookings/availability. */
@Schema(description = "The result of checking whether a room is free over a period")
public record AvailabilityPayload(
        @Schema(description = "The room that was checked", example = "3")
        Long roomId,
        @Schema(description = "true when the room is free over the requested period", example = "true")
        boolean available,
        @Schema(description = "The period that was checked", example = "2026-12-25 14:30:00 - 2026-12-25 16:30:00")
        String period,
        @Schema(description = "The result as text. An UPPERCASE vocabulary, distinct from the room's stored status",
                allowableValues = {"FREE", "BUSY"}, example = "FREE")
        String status) {

    public AvailabilityPayload(Long roomId, boolean available, String period) {
        this(roomId, available, period, available ? "FREE" : "BUSY");
    }
}
