package com.prenotazioni.dto;

/** Conferma di eliminazione aula (DELETE /api/admin/rooms/{id}). */
public class DeletedRoomResponse {
    private final Long deletedRoomId;

    public DeletedRoomResponse(Long deletedRoomId) {
        this.deletedRoomId = deletedRoomId;
    }

    public Long getDeletedRoomId() { return deletedRoomId; }
}
