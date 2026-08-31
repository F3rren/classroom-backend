package com.prenotazioni.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Conferma di eliminazione utente (DELETE /api/admin/delete/{id}). */
@Getter
@AllArgsConstructor
public class DeletedUserResponse {
    private final Long deletedUserId;
}
