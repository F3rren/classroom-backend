package com.prenotazioni.dto;

import com.prenotazioni.model.Utente;

/** Riepilogo di un utente appena creato da un admin (POST /api/admin/register). */
public class UserRegisterAck {
    private final Long userId;
    private final String email;
    private final String username;
    private final String ruolo;

    public UserRegisterAck(Utente utente) {
        this.userId = utente.getId();
        this.email = utente.getEmail();
        this.username = utente.getUsername();
        this.ruolo = utente.getRuolo() != null ? utente.getRuolo() : "USER";
    }

    public Long getUserId() { return userId; }
    public String getEmail() { return email; }
    public String getUsername() { return username; }
    public String getRuolo() { return ruolo; }
}
