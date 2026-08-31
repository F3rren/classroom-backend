package com.prenotazioni.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contratto esterno di Ruolo e StatoAula.
 *
 * Il test su toAuthority() e' il piu' importante del file: le espressioni
 * @PreAuthorize("hasRole('ADMIN')") sono stringhe SpEL che il compilatore non verifica,
 * quindi se qualcuno cambiasse il nome della costante o il prefisso l'autorizzazione si
 * romperebbe in silenzio, senza alcun errore di compilazione. Qui quel legame e' fissato.
 */
class RuoloTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void authorityMatchesTheStringUsedInPreAuthorize() {
        // hasRole('ADMIN') cerca l'authority "ROLE_ADMIN"
        assertThat(Ruolo.ADMIN.toAuthority()).isEqualTo("ROLE_ADMIN");
        assertThat(Ruolo.USER.toAuthority()).isEqualTo("ROLE_USER");
    }

    @Test
    void serializesLowercaseBecauseTheFrontendComparesWithAdmin() throws Exception {
        // il frontend fa: user?.ruolo === "admin"
        assertThat(objectMapper.writeValueAsString(Ruolo.ADMIN)).isEqualTo("\"admin\"");
        assertThat(objectMapper.writeValueAsString(Ruolo.USER)).isEqualTo("\"user\"");
    }

    @Test
    void matchesTheDatabaseCheckConstraint() {
        // utente_ruolo_check ammette esattamente 'admin' e 'user'
        assertThat(java.util.Arrays.stream(Ruolo.values()).map(Ruolo::getValore))
                .containsExactlyInAnyOrder("admin", "user");
    }

    @Test
    void parsingIsCaseInsensitive() {
        assertThat(Ruolo.da("ADMIN")).isEqualTo(Ruolo.ADMIN);
        assertThat(Ruolo.da(" Admin ")).isEqualTo(Ruolo.ADMIN);
        assertThat(Ruolo.da(null)).isNull();
        assertThatThrownBy(() -> Ruolo.da("superuser")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void statoAulaKeepsItsOwnLowercaseVocabulary() {
        // aula_stato_check: 4 valori, diversi da quelli della prenotazione
        assertThat(java.util.Arrays.stream(StatoAula.values()).map(StatoAula::getValore))
                .containsExactlyInAnyOrder("libera", "occupata", "bloccata", "manutenzione");
    }

    @Test
    void statoAulaSerializesLowercase() throws Exception {
        assertThat(objectMapper.writeValueAsString(StatoAula.OCCUPATA)).isEqualTo("\"occupata\"");
        assertThat(objectMapper.writeValueAsString(StatoAula.LIBERA)).isEqualTo("\"libera\"");
    }
}
