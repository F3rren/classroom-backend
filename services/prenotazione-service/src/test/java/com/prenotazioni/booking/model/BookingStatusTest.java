package com.prenotazioni.booking.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contratto esterno dell'enum. Questi test non sono cosmetici: il frontend compilato
 * confronta le stringhe minuscole esatte ("cancelled", "blocked", "booked", "free")
 * e la colonna DB ha un CHECK constraint sugli stessi valori. Se qualcuno rinominasse le
 * costanti o togliesse @JsonValue, il JSON diventerebbe maiuscolo e romperebbe entrambi:
 * qui si blocca quel comportamento.
 */
class BookingStatusTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesToLowercaseForTheFrontend() throws Exception {
        assertThat(objectMapper.writeValueAsString(BookingStatus.BOOKED)).isEqualTo("\"booked\"");
        assertThat(objectMapper.writeValueAsString(BookingStatus.CANCELLED)).isEqualTo("\"cancelled\"");
        assertThat(objectMapper.writeValueAsString(BookingStatus.BLOCKED)).isEqualTo("\"blocked\"");
        assertThat(objectMapper.writeValueAsString(BookingStatus.MAINTENANCE)).isEqualTo("\"maintenance\"");
    }

    @Test
    void everyConstantMatchesTheDatabaseCheckConstraint() {
        // booking_status_check ammette esattamente questi cinque valori
        assertThat(java.util.Arrays.stream(BookingStatus.values()).map(BookingStatus::getValue))
                .containsExactlyInAnyOrder("booked", "confirmed", "blocked", "maintenance", "cancelled");
    }

    @Test
    void parsingIsCaseInsensitiveLikeTheOldStringComparisons() {
        assertThat(BookingStatus.from("CANCELLED")).isEqualTo(BookingStatus.CANCELLED);
        assertThat(BookingStatus.from("  Booked  ")).isEqualTo(BookingStatus.BOOKED);
        assertThat(BookingStatus.from(null)).isNull();
    }

    @Test
    void unValoreSconosciutoVieneRifiutato() {
        assertThatThrownBy(() -> BookingStatus.from("inventato"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void onlyPrenotataIsConsideredActive() {
        assertThat(BookingStatus.BOOKED.isActive()).isTrue();
        assertThat(BookingStatus.CANCELLED.isActive()).isFalse();
        assertThat(BookingStatus.BLOCKED.isActive()).isFalse();
    }

    @Test
    void gliInterventiDellAdminSonoBloccoEManutenzione() {
        assertThat(BookingStatus.BLOCKED.isAdminIntervention()).isTrue();
        assertThat(BookingStatus.MAINTENANCE.isAdminIntervention()).isTrue();
        assertThat(BookingStatus.BOOKED.isAdminIntervention()).isFalse();
    }
}
