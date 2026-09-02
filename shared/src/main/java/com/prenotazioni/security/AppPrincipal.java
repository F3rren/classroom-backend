package com.prenotazioni.security;

import com.prenotazioni.model.Ruolo;
import org.springframework.security.core.AuthenticatedPrincipal;

/**
 * Principal costruito dai claim gia' verificati del JWT (id, email, ruolo),
 * cosi' i controller possono leggere id/ruolo senza un round-trip al DB per richiesta.
 * getName() ritorna l'email per restare compatibile con il codice esistente che chiama
 * Authentication.getName() aspettandosi l'email dell'utente.
 */
public record AppPrincipal(Long id, String email, String nome, String ruolo) implements AuthenticatedPrincipal {

    @Override
    public String getName() {
        return email;
    }

    public boolean isAdmin() {
        return Ruolo.ADMIN.getValore().equalsIgnoreCase(ruolo);
    }
}
