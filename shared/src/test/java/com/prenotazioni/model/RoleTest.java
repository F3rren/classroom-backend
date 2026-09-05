package com.prenotazioni.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contratto esterno di Ruolo.
 *
 * Il test su toAuthority() e' il piu' importante del file: le espressioni
 * @PreAuthorize("hasRole('ADMIN')") sono stringhe SpEL che il compilatore non verifica,
 * quindi se qualcuno cambiasse il nome della costante o il prefisso l'autorizzazione si
 * romperebbe in silenzio, senza alcun errore di compilazione. Qui quel legame e' fissato.
 */
class RoleTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void authorityMatchesTheStringUsedInPreAuthorize() {
        // hasRole('ADMIN') cerca l'authority "ROLE_ADMIN"
        assertThat(Role.ADMIN.toAuthority()).isEqualTo("ROLE_ADMIN");
        assertThat(Role.USER.toAuthority()).isEqualTo("ROLE_USER");
    }

    @Test
    void serializesLowercaseBecauseTheFrontendComparesWithAdmin() throws Exception {
        // il frontend fa: user?.ruolo === "admin"
        assertThat(objectMapper.writeValueAsString(Role.ADMIN)).isEqualTo("\"admin\"");
        assertThat(objectMapper.writeValueAsString(Role.USER)).isEqualTo("\"user\"");
    }

    @Test
    void matchesTheDatabaseCheckConstraint() {
        // utente_ruolo_check ammette esattamente 'admin' e 'user'
        assertThat(java.util.Arrays.stream(Role.values()).map(Role::getValue))
                .containsExactlyInAnyOrder("admin", "user");
    }

    @Test
    void parsingIsCaseInsensitive() {
        assertThat(Role.da("ADMIN")).isEqualTo(Role.ADMIN);
        assertThat(Role.da(" Admin ")).isEqualTo(Role.ADMIN);
        assertThat(Role.da(null)).isNull();
        assertThatThrownBy(() -> Role.da("superuser")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theJpaConverterWritesTheLowercaseValueOnDisk() {
        // e' cio' che tiene la colonna dentro utente_ruolo_check
        Role.JpaConverter converter = new Role.JpaConverter();

        assertThat(converter.convertToDatabaseColumn(Role.ADMIN)).isEqualTo("admin");
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute("user")).isEqualTo(Role.USER);
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }
}
