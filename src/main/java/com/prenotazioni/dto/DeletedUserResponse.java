package com.prenotazioni.dto;

/** Conferma di eliminazione utente (DELETE /api/admin/delete/{id}). */
public class DeletedUserResponse {
    private final Long deletedUserId;

    public DeletedUserResponse(Long deletedUserId) {
        this.deletedUserId = deletedUserId;
    }

    public Long getDeletedUserId() { return deletedUserId; }
}
