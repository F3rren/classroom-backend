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
 * il claim "ruolo" del JWT e AppPrincipal restano String: sono formato di trasporto,
 * non il modello di dominio.
 */
public enum Role {

    ADMIN("admin"),
    USER("user");

    private final String valore;

    Role(String valore) {
        this.valore = valore;
    }

    /** Valore minuscolo usato in JSON, nel database e nel claim JWT. */
    @JsonValue
    public String getValore() {
        return valore;
    }

    @JsonCreator
    public static Role da(String valore) {
        if (valore == null) {
            return null;
        }
        String normalizzato = valore.trim().toLowerCase(Locale.ROOT);
        for (Role ruolo : values()) {
            if (ruolo.valore.equals(normalizzato)) {
                return ruolo;
            }
        }
        throw new IllegalArgumentException("Ruolo non valido: " + valore);
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
        public String convertToDatabaseColumn(Role ruolo) {
            return ruolo == null ? null : ruolo.getValore();
        }

        @Override
        public Role convertToEntityAttribute(String valore) {
            return da(valore);
        }
    }
}
