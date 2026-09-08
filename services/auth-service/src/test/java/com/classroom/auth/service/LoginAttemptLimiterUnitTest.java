package com.classroom.auth.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The login attempt limiter.
 *
 * The test that matters is the one on the cleanup: this class exists because the map was
 * never emptied, and without a test pinning that down the defect can come back unnoticed -
 * it produces no errors, only memory that never comes back.
 */
class LoginAttemptLimiterUnitTest {

    private LoginAttemptLimiter attemptLimiter(int maxAttempts, long windowMs, int cap) {
        return new LoginAttemptLimiter(maxAttempts, windowMs, cap);
    }

    @Test
    void letsAttemptsThroughUpToTheLimit() {
        LoginAttemptLimiter l = attemptLimiter(3, 60_000, 1000);

        assertThat(l.tooManyAttempts("a")).isFalse();
        assertThat(l.tooManyAttempts("a")).isFalse();
        assertThat(l.tooManyAttempts("a")).isFalse();
        assertThat(l.tooManyAttempts("a")).isTrue();
    }

    @Test
    void everyKeyHasItsOwnCounter() {
        // If the counters were not separate, a single attacker would be enough to block
        // everybody else's login.
        LoginAttemptLimiter l = attemptLimiter(1, 60_000, 1000);

        l.tooManyAttempts("primo");
        assertThat(l.tooManyAttempts("primo")).isTrue();
        assertThat(l.tooManyAttempts("secondo")).isFalse();
    }

    @Test
    void theWindowReopens() {
        // A negative window and not zero: with zero, two calls in the same millisecond give
        // a difference of 0, which does not exceed the threshold, and the test would depend
        // on the clock. With -1 the condition is true by construction.
        LoginAttemptLimiter l = attemptLimiter(1, -1, 1000);

        l.tooManyAttempts("a");
        assertThat(l.tooManyAttempts("a")).isFalse();
    }

    @Test
    void theCleanupRemovesExpiredKeys() {
        // THE regression to keep closed. No key ever left before, and the email half of the
        // key is chosen by the caller: memory grew on request.
        LoginAttemptLimiter l = attemptLimiter(5, 1000, 1000);

        for (int i = 0; i < 200; i++) {
            l.tooManyAttempts("indirizzo-inventato-" + i + "@esempio.it");
        }
        assertThat(l.trackedKeys()).isEqualTo(200);

        l.purgeExpired(System.currentTimeMillis() + 5000);

        assertThat(l.trackedKeys()).isZero();
    }

    @Test
    void theCleanupSparesKeysStillInsideTheWindow() {
        // Cleaning too much would be the opposite mistake: it would reset the counters of
        // whoever is attacking right now, which are precisely the ones needed.
        LoginAttemptLimiter l = attemptLimiter(5, 600_000, 1000);

        l.tooManyAttempts("ancora-viva@esempio.it");
        l.purgeExpired(System.currentTimeMillis());

        assertThat(l.trackedKeys()).isEqualTo(1);
    }

    @Test
    void atTheCapItCleansUpBeforeGivingUp() {
        // The cap must not trip while there is expired material to throw away: free it
        // first, and only stop recording if the cleanup still leaves you at the limit.
        // Same reason as above: a 1 ms window would be expired or not depending on how fast
        // the loop runs. Negative, they are expired by construction.
        LoginAttemptLimiter l = attemptLimiter(5, -1, 50);

        for (int i = 0; i < 50; i++) {
            l.tooManyAttempts("vecchia-" + i);
        }
        assertThat(l.trackedKeys()).isEqualTo(50);

        l.tooManyAttempts("nuova");

        assertThat(l.trackedKeys()).isLessThan(50);
    }

    @Test
    void atTheCapAlreadyKnownKeysStayLimited() {
        // Failing open applies only to NEW keys: whoever is already attacking keeps being
        // counted, otherwise filling the map would be the way to switch the limiter off.
        LoginAttemptLimiter l = attemptLimiter(1, 600_000, 2);

        l.tooManyAttempts("nota");
        assertThat(l.tooManyAttempts("nota")).isTrue();

        l.tooManyAttempts("seconda");
        // the cap is full and nothing has expired: the third key is not recorded
        assertThat(l.tooManyAttempts("terza")).isFalse();
        assertThat(l.trackedKeys()).isEqualTo(2);

        // but the one already known keeps being limited
        assertThat(l.tooManyAttempts("nota")).isTrue();
    }

    @Test
    void theRetryDelayIsWhatIsLeftOfTheWindow() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(1, 60_000L, 1000);
        limiter.tooManyAttempts("chi@test.it");

        // The window has just opened, so what is left of it is essentially all of it. This
        // is the value a client reads from Retry-After and waits on.
        assertThat(limiter.retryAfterSeconds("chi@test.it")).isBetween(59L, 60L);
    }

    @Test
    void anUnknownKeyIsToldToWaitAWholeWindow() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(1, 60_000L, 1000);

        // The honest upper bound: it is what the caller would wait anyway if the window
        // started now.
        assertThat(limiter.retryAfterSeconds("mai-visto")).isEqualTo(60L);
    }

    @Test
    void theRetryDelayIsNeverZero() {
        // A Retry-After of 0 invites the immediate retry the limit exists to refuse, so a
        // window about to close still answers one second.
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(1, 1L, 1000);
        limiter.tooManyAttempts("chi@test.it");

        assertThat(limiter.retryAfterSeconds("chi@test.it")).isGreaterThanOrEqualTo(1L);
    }
}
