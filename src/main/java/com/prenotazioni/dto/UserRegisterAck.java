package com.prenotazioni.dto;

import com.prenotazioni.model.Utente;
import com.prenotazioni.model.Ruolo;
import lombok.Getter;

/** Riepilogo di un utente appena creato da un admin (POST /api/admin/register). */
@Getter
public class UserRegisterAck {
    private final Long userId;
    private final String email;
    private final String username;
    private final String ruolo;

    public UserRegisterAck(Utente utente) {
        this.userId = utente.getId();
        this.email = utente.getEmail();
        this.username = utente.getUsername();
        this.ruolo = utente.getRuolo() != null ? utente.getRuolo().getValore() : "USER";
    }
}
