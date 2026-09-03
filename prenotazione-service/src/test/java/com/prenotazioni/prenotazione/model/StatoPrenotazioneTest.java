package com.prenotazioni.prenotazione.model;

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
class StatoPrenotazioneTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesToLowercaseForTheFrontend() throws Exception {
        assertThat(objectMapper.writeValueAsString(StatoPrenotazione.PRENOTATA)).isEqualTo("\"prenotata\"");
        assertThat(objectMapper.writeValueAsString(StatoPrenotazione.ANNULLATA)).isEqualTo("\"annullata\"");
        assertThat(objectMapper.writeValueAsString(StatoPrenotazione.BLOCCATA)).isEqualTo("\"bloccata\"");
        assertThat(objectMapper.writeValueAsString(StatoPrenotazione.MANUTENZIONE)).isEqualTo("\"manutenzione\"");
    }

    @Test
    void everyConstantMatchesTheDatabaseCheckConstraint() {
        // prenotazione_stato_check ammette esattamente questi cinque valori
        assertThat(java.util.Arrays.stream(StatoPrenotazione.values()).map(StatoPrenotazione::getValore))
                .containsExactlyInAnyOrder("prenotata", "confermata", "bloccata", "manutenzione", "annullata");
    }

    @Test
    void parsingIsCaseInsensitiveLikeTheOldStringComparisons() {
        assertThat(StatoPrenotazione.da("ANNULLATA")).isEqualTo(StatoPrenotazione.ANNULLATA);
        assertThat(StatoPrenotazione.da("  Prenotata  ")).isEqualTo(StatoPrenotazione.PRENOTATA);
        assertThat(StatoPrenotazione.da(null)).isNull();
    }

    @Test
    void unknownValueIsRejected() {
        assertThatThrownBy(() -> StatoPrenotazione.da("inventato"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void onlyPrenotataIsConsideredActive() {
        assertThat(StatoPrenotazione.PRENOTATA.isAttiva()).isTrue();
        assertThat(StatoPrenotazione.ANNULLATA.isAttiva()).isFalse();
        assertThat(StatoPrenotazione.BLOCCATA.isAttiva()).isFalse();
    }

    @Test
    void adminInterventionsAreBlockedAndMaintenance() {
        assertThat(StatoPrenotazione.BLOCCATA.isInterventoAdmin()).isTrue();
        assertThat(StatoPrenotazione.MANUTENZIONE.isInterventoAdmin()).isTrue();
        assertThat(StatoPrenotazione.PRENOTATA.isInterventoAdmin()).isFalse();
    }
}
