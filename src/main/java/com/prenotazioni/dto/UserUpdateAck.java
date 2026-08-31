package com.prenotazioni.dto;

import com.prenotazioni.model.Utente;

/** Riepilogo di un utente appena modificato da un admin (PUT /api/admin/users/{id}). */
public class UserUpdateAck {
    private final Long userId;
    private final String email;
    private final String username;
    private final String nome;
    private final String ruolo;

    public UserUpdateAck(Utente utente) {
        this.userId = utente.getId();
        this.email = utente.getEmail();
        this.username = utente.getUsername();
        this.nome = utente.getNome() != null ? utente.getNome() : "";
        this.ruolo = utente.getRuolo() != null ? utente.getRuolo() : "USER";
    }

    public Long getUserId() { return userId; }
    public String getEmail() { return email; }
    public String getUsername() { return username; }
    public String getNome() { return nome; }
    public String getRuolo() { return ruolo; }
}
