package com.prenotazioni.booking.dto;

import lombok.Value;

/** Wrapper per GET /api/rooms/stats: { "statistics": {...} }. */
@Value
public class RoomStatsPayload {
    RoomStats statistics;
}
