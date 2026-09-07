package com.prenotazioni.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/** Risposta di GET /api/bookings/availability. */
@Getter
@Schema(description = "The result of checking whether a room is free over a period")
public class AvailabilityPayload {
    @Schema(description = "The room that was checked", example = "3")
    private final Long roomId;
    @Schema(description = "true when the room is free over the requested period", example = "true")
    private final boolean available;
    @Schema(description = "The period that was checked", example = "2026-12-25 14:30:00 - 2026-12-25 16:30:00")
    private final String period;
    @Schema(description = "The result as text. An UPPERCASE vocabulary, distinct from the room's stored status",
            allowableValues = {"FREE", "BUSY"}, example = "FREE")
    private final String status;

    public AvailabilityPayload(Long roomId, boolean available, String period) {
        this.roomId = roomId;
        this.available = available;
        this.period = period;
        this.status = available ? "FREE" : "BUSY";
    }
}
