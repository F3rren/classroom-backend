package com.prenotazioni.booking.model;

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
class RoomStatusTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void statoAulaKeepsItsOwnLowercaseVocabulary() {
        // room_status_check: 4 valori, diversi da quelli della prenotazione
        assertThat(java.util.Arrays.stream(RoomStatus.values()).map(RoomStatus::getValue))
                .containsExactlyInAnyOrder("free", "busy", "blocked", "maintenance");
    }

    @Test
    void statoAulaSerializesLowercase() throws Exception {
        assertThat(objectMapper.writeValueAsString(RoomStatus.BUSY)).isEqualTo("\"busy\"");
        assertThat(objectMapper.writeValueAsString(RoomStatus.FREE)).isEqualTo("\"free\"");
    }
}
