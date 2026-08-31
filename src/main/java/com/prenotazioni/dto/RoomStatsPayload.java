package com.prenotazioni.dto;

/** Wrapper per GET /api/rooms/stats: { "statistics": {...} }. */
public class RoomStatsPayload {
    private final RoomStats statistics;

    public RoomStatsPayload(RoomStats statistics) {
        this.statistics = statistics;
    }

    public RoomStats getStatistics() { return statistics; }
}
