package com.prenotazioni.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Conferma di eliminazione aula (DELETE /api/admin/rooms/{id}). */
@Getter
@AllArgsConstructor
public class DeletedRoomResponse {
    private final Long deletedRoomId;
}
