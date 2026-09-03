package com.prenotazioni.auth.dto;

import lombok.Value;

/** Conferma di eliminazione utente (DELETE /api/admin/utenti/{id}). */
@Value
public class DeletedUserResponse {
    Long deletedUserId;
}
