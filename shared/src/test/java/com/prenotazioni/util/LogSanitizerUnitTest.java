package com.prenotazioni.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LogSanitizer's happy path is already crossed by every login; what is covered here are the
 * guards on degenerate input, which cannot be provoked over HTTP because Bean Validation
 * rejects malformed emails first.
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
        // atIndex == 0: there is no local part left to show
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
