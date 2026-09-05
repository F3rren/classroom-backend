package com.prenotazioni.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Locale;

/**
 * Ruolo applicativo di un utente, persistito minuscolo (CHECK constraint
 * utente_ruolo_check) e letto minuscolo dal frontend, che fa user?.ruolo === "admin".
 *
 * ATTENZIONE al confine con Spring Security: qui NON si tocca il formato delle
 * authority. Spring usa il prefisso e il maiuscolo ("ROLE_ADMIN"), che JwtAuthFilter
 * costruisce a partire da questo valore, e le espressioni @PreAuthorize("hasRole('ADMIN')")
 * sono stringhe SpEL che il compilatore non verifica: cambiare il valore di questo enum
 * senza aggiornarle romperebbe l'autorizzazione in modo silenzioso. Per lo stesso motivo
 * il claim "role" del JWT e AppPrincipal restano String: sono formato di trasporto,
 * non il modello di dominio.
 */
public enum Role {

    ADMIN("admin"),
    USER("user");

    private final String value;

    Role(String value) {
        this.value = value;
    }

    /** Valore minuscolo usato in JSON, nel database e nel claim JWT. */
    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static Role da(String value) {
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
     * Centralizzato qui in modo che il prefisso non venga piu' ricostruito a mano.
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
            return da(value);
        }
    }
}
