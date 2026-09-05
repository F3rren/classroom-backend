package com.prenotazioni.booking.dto;

import lombok.Value;

/** Conferma di eliminazione aula (DELETE /api/admin/rooms/{id}). */
@Value
public class DeletedRoomResponse {
    Long deletedRoomId;
}
