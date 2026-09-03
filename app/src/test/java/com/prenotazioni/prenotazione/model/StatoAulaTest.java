package com.prenotazioni.prenotazione.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contratto esterno di StatoAula.
 *
 * Questi due test stavano dentro RuoloTest, che ne copriva due enum diversi. La
 * modularizzazione ha separato i due casi: Ruolo e' comune a tutti i servizi e vive in
 * shared, StatoAula appartiene al dominio delle aule e resta qui.
 */
class StatoAulaTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

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
