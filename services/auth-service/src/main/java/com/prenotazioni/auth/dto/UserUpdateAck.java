package com.prenotazioni.auth.dto;

import com.prenotazioni.model.Ruolo;
import com.prenotazioni.auth.model.Utente;
import lombok.Getter;

/** Riepilogo di un utente appena modificato da un admin (PUT /api/admin/utenti/{id}). */
@Getter
public class UserUpdateAck {
    private final Long userId;
    private final String email;
    private final String username;
    private final String nome;
    private final Ruolo ruolo;

    public UserUpdateAck(Utente utente) {
        this.userId = utente.getId();
        this.email = utente.getEmail();
        this.username = utente.getUsername();
        this.nome = utente.getNome() != null ? utente.getNome() : "";
        this.ruolo = utente.getRuolo();
    }
}
