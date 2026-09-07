package com.prenotazioni.booking.dto;

import lombok.Value;

/** The wrapper for GET /api/rooms/stats: { "statistics": {...} }. */
@Value
public class RoomStatsPayload {
    RoomStats statistics;
}
