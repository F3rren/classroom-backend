package com.classroom.booking.dto;

/** The wrapper for GET /api/rooms/stats: { "statistics": {...} }. */
public record RoomStatsPayload(RoomStats statistics) {
}
