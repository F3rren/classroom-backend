package com.prenotazioni.auth.dto;

import com.prenotazioni.model.Ruolo;
import com.prenotazioni.auth.model.Utente;
import lombok.Getter;

/** Riepilogo di un utente appena creato da un admin (POST /api/admin/register). */
@Getter
public class UserRegisterAck {
    private final Long userId;
    private final String email;
    private final String username;
    private final Ruolo ruolo;

    public UserRegisterAck(Utente utente) {
        this.userId = utente.getId();
        this.email = utente.getEmail();
        this.username = utente.getUsername();
        this.ruolo = utente.getRuolo();
    }
}
