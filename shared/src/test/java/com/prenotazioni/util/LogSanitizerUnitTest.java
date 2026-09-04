package com.prenotazioni.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Il percorso felice di LogSanitizer e' gia' attraversato da ogni login; qui si coprono
 * i guard su input degeneri, che non e' possibile provocare via HTTP perche' Bean
 * Validation rifiuta prima le email malformate.
 */
class LogSanitizerUnitTest {

    @Test
    void mascheraLaParteLocaleEMantieneIlDominio() {
        assertThat(LogSanitizer.maskEmail("mario.rossi@example.it")).isEqualTo("m***@example.it");
    }

    @Test
    void nullEmailCollapsesToStars() {
        assertThat(LogSanitizer.maskEmail(null)).isEqualTo("***");
    }

    @Test
    void tooShortEmailCollapsesToStars() {
        assertThat(LogSanitizer.maskEmail("ab")).isEqualTo("***");
    }

    @Test
    void stringWithoutAtSignCollapsesToStars() {
        assertThat(LogSanitizer.maskEmail("nessuna-chiocciola")).isEqualTo("***");
    }

    @Test
    void emailStartingWithAtSignCollapsesToStars() {
        // atIndex == 0: non c'e' parte locale da mostrare
        assertThat(LogSanitizer.maskEmail("@example.it")).isEqualTo("***");
    }

    @Test
    void maskUsernameKeepsOnlyTheInitial() {
        assertThat(LogSanitizer.maskUsername("mrossi")).isEqualTo("m***");
    }

    @Test
    void nullOrEmptyUsernameCollapsesToStars() {
        assertThat(LogSanitizer.maskUsername(null)).isEqualTo("***");
        assertThat(LogSanitizer.maskUsername("")).isEqualTo("***");
    }
}
