package com.classroom.booking.dto;

/** Confirmation that a room was deleted (DELETE /api/admin/rooms/{id}). */
public record DeletedRoomResponse(Long deletedRoomId) {
}
