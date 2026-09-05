package com.prenotazioni.auth.dto;

import com.prenotazioni.model.Role;
import com.prenotazioni.auth.model.User;
import lombok.Getter;

/** Riepilogo di un utente appena modificato da un admin (PUT /api/admin/utenti/{id}). */
@Getter
public class UserUpdateAck {
    private final Long userId;
    private final String email;
    private final String username;
    private final String nome;
    private final Role ruolo;

    public UserUpdateAck(User utente) {
        this.userId = utente.getId();
        this.email = utente.getEmail();
        this.username = utente.getUsername();
        this.nome = utente.getNome() != null ? utente.getNome() : "";
        this.ruolo = utente.getRuolo();
    }
}
