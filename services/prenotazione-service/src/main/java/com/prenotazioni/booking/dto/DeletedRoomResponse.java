package com.prenotazioni.booking.dto;

import lombok.Value;

/** Confirmation that a room was deleted (DELETE /api/admin/rooms/{id}). */
@Value
public class DeletedRoomResponse {
    Long deletedRoomId;
}
