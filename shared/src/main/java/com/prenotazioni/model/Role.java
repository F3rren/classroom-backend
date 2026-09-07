package com.prenotazioni.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Locale;

/**
 * A user's application role, stored lowercase (CHECK constraint user_role_check) and read
 * lowercase by a client, which compares user?.role === "admin".
 *
 * MIND the boundary with Spring Security: the format of the authorities is NOT touched here.
 * Spring uses the prefix and uppercase ("ROLE_ADMIN"), which JwtAuthFilter builds from this
 * value, and the @PreAuthorize("hasRole('ADMIN')") expressions are SpEL strings the compiler
 * does not check: changing this enum's value without updating them would break authorisation
 * silently. For the same reason the JWT's "role" claim and AppPrincipal stay Strings: they
 * are a transport format, not the domain model.
 */
public enum Role {

    ADMIN("admin"),
    USER("user");

    private final String value;

    Role(String value) {
        this.value = value;
    }

    /** The lowercase value used in JSON, in the database and in the JWT claim. */
    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static Role from(String value) {
        if (value == null) {
            return null;
        }
        String normalizzato = value.trim().toLowerCase(Locale.ROOT);
        for (Role role : values()) {
            if (role.value.equals(normalizzato)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Ruolo non valido: " + value);
    }

    /**
     * Nome dell'authority attesa da Spring Security (hasRole('ADMIN') cerca "ROLE_ADMIN").
     * Centralised here so the prefix is never rebuilt by hand again.
     */
    public String toAuthority() {
        return "ROLE_" + name();
    }

    @Converter(autoApply = true)
    public static class JpaConverter implements AttributeConverter<Role, String> {

        @Override
        public String convertToDatabaseColumn(Role role) {
            return role == null ? null : role.getValue();
        }

        @Override
        public Role convertToEntityAttribute(String value) {
            return from(value);
        }
    }
}
