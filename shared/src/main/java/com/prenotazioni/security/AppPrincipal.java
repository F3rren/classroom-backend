package com.prenotazioni.security;

import com.prenotazioni.model.Role;
import org.springframework.security.core.AuthenticatedPrincipal;

/**
 * The principal built from the JWT's already verified claims (id, email, role), so the
 * controllers can read the id and the role without a database round trip per request.
 *
 * getName() returns the email, to stay compatible with existing code that calls
 * Authentication.getName() expecting the user's email address.
 */
public record AppPrincipal(Long id, String email, String username, String name, String role)
        implements AuthenticatedPrincipal {

    @Override
    public String getName() {
        return email;
    }

    public boolean isAdmin() {
        return Role.ADMIN.getValue().equalsIgnoreCase(role);
    }
}
