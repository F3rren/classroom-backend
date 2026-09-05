package com.prenotazioni.booking.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contratto esterno dell'enum. Questi test non sono cosmetici: il frontend compilato
 * confronta le stringhe minuscole esatte ("annullata", "bloccata", "prenotata", "libera")
 * e la colonna DB ha un CHECK constraint sugli stessi valori. Se qualcuno rinominasse le
 * costanti o togliesse @JsonValue, il JSON diventerebbe maiuscolo e romperebbe entrambi:
 * qui si blocca quel comportamento.
 */
class BookingStatusTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesToLowercaseForTheFrontend() throws Exception {
        assertThat(objectMapper.writeValueAsString(BookingStatus.PRENOTATA)).isEqualTo("\"prenotata\"");
        assertThat(objectMapper.writeValueAsString(BookingStatus.ANNULLATA)).isEqualTo("\"annullata\"");
        assertThat(objectMapper.writeValueAsString(BookingStatus.BLOCCATA)).isEqualTo("\"bloccata\"");
        assertThat(objectMapper.writeValueAsString(BookingStatus.MANUTENZIONE)).isEqualTo("\"manutenzione\"");
    }

    @Test
    void everyConstantMatchesTheDatabaseCheckConstraint() {
        // prenotazione_stato_check ammette esattamente questi cinque valori
        assertThat(java.util.Arrays.stream(BookingStatus.values()).map(BookingStatus::getValue))
                .containsExactlyInAnyOrder("prenotata", "confermata", "bloccata", "manutenzione", "annullata");
    }

    @Test
    void parsingIsCaseInsensitiveLikeTheOldStringComparisons() {
        assertThat(BookingStatus.da("ANNULLATA")).isEqualTo(BookingStatus.ANNULLATA);
        assertThat(BookingStatus.da("  Prenotata  ")).isEqualTo(BookingStatus.PRENOTATA);
        assertThat(BookingStatus.da(null)).isNull();
    }

    @Test
    void unValoreSconosciutoVieneRifiutato() {
        assertThatThrownBy(() -> BookingStatus.da("inventato"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void onlyPrenotataIsConsideredActive() {
        assertThat(BookingStatus.PRENOTATA.isAttiva()).isTrue();
        assertThat(BookingStatus.ANNULLATA.isAttiva()).isFalse();
        assertThat(BookingStatus.BLOCCATA.isAttiva()).isFalse();
    }

    @Test
    void gliInterventiDellAdminSonoBloccoEManutenzione() {
        assertThat(BookingStatus.BLOCCATA.isInterventoAdmin()).isTrue();
        assertThat(BookingStatus.MANUTENZIONE.isInterventoAdmin()).isTrue();
        assertThat(BookingStatus.PRENOTATA.isInterventoAdmin()).isFalse();
    }
}
