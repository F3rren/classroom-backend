package com.prenotazioni.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Wrapper per GET /api/rooms/stats: { "statistics": {...} }. */
@Getter
@AllArgsConstructor
public class RoomStatsPayload {
    private final RoomStats statistics;
}
