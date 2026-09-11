package com.classroom.booking.dto;

/**
 * A minimal wrapper around a single room, reused by GET /api/admin/rooms/{id} (the raw Room)
 * e GET /api/rooms/{id}/detailed (RoomDetailsResponse).
 */
public record RoomWrapper<T>(T room) {
}
