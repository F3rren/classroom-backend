package com.prenotazioni.auth.dto;

import com.prenotazioni.model.Role;
import com.prenotazioni.auth.model.User;
import lombok.Getter;

/** Riepilogo di un utente appena creato da un admin (POST /api/admin/users). */
@Getter
public class UserRegisterAck {
    private final Long userId;
    private final String email;
    private final String username;
    private final Role role;

    public UserRegisterAck(User user) {
        this.userId = user.getId();
        this.email = user.getEmail();
        this.username = user.getUsername();
        this.role = user.getRole();
    }
}
