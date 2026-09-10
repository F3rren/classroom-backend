package com.classroom.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Counter of failed login attempts, fixed-window and in memory.
 *
 * It used to live inside AuthController as a static field, and had two defects that held
 * hands with each other.
 *
 * THE FIRST, and the reason this class exists: the map was NEVER emptied. A single write,
 * computeIfAbsent, and no removal: every pair ever seen stayed in forever, and the email
 * half of the key is chosen by the caller. Growth therefore did not depend on the number of
 * real users but on how many different strings somebody decided to send to a public
 * endpoint.
 *
 * THE SECOND was the static itself: the tests had to clear the map by hand between cases
 * because surefire reuses the JVM, and the test profile raised max-attempts to 1000 so the
 * limit would not trip in the other classes. An ordinary component, one per context, removes
 * the problem instead of working around it.
 *
 * ON FAILING OPEN. If the cleanup is not enough - that is, if there really are tens of
 * thousands of keys still inside the window - this class stops recording new keys instead of
 * carrying on growing. That is deliberate: a rate limiter is a better defence, not the only
 * one, and staying up without limiting is worth more than falling over on memory exhaustion
 * and taking the legitimate logins down too. Keys already known keep being limited.
 */
@Component
public class LoginAttemptLimiter {

    private static final Logger logger = LoggerFactory.getLogger(LoginAttemptLimiter.class);

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    private final int maxAttempts;
    private final long windowMs;
    private final int maxKeys;

    /** So the same warning is not repeated on every request while the map is full. */
    private volatile long lastWarning;

    public LoginAttemptLimiter(
            @Value("${auth.rate-limit.max-attempts:5}") int maxAttempts,
            @Value("${auth.rate-limit.window-ms:60000}") long windowMs,
            @Value("${auth.rate-limit.max-entries:50000}") int maxKeys) {
        this.maxAttempts = maxAttempts;
        this.windowMs = windowMs;
        this.maxKeys = maxKeys;
    }

    /** Records an attempt and says whether the key has gone over the limit. */
    public boolean tooManyAttempts(String key) {
        long now = System.currentTimeMillis();

        Window window = windows.get(key);
        if (window == null) {
            if (windows.size() >= maxKeys) {
                purgeExpired(now);
            }
            if (windows.size() >= maxKeys) {
                warnOccasionally(now);
                return false;
            }
            window = windows.computeIfAbsent(key, k -> new Window(now));
        }

        synchronized (window) {
            if (now - window.startTime > windowMs) {
                window.startTime = now;
                window.attempts = 0;
            }
            window.attempts++;
            return window.attempts > maxAttempts;
        }
    }

    /**
     * How many seconds until the key's window reopens, for the Retry-After header.
     *
     * RFC 9110 §10.2.3 is what makes a 429 actionable: without it the caller is told to
     * "riprova tra qualche minuto" and has to guess how many. The limiter is the only thing
     * that knows, so it is the only thing that can say.
     *
     * A key nobody has recorded, or one whose window has already closed, answers with the
     * full window: it is the honest upper bound, and it is what the caller would wait
     * anyway if it started counting now.
     *
     * The value is ADVISORY, and asking for it separately from tooManyAttempts is what
     * makes it so: between the two calls the window can roll over and the answer be an
     * instant too generous. That is the right trade for a header nobody makes a decision
     * on beyond when to try again.
     */
    public long retryAfterSeconds(String key) {
        Window window = windows.get(key);
        long remainingMs = windowMs;
        if (window != null) {
            synchronized (window) {
                long elapsed = System.currentTimeMillis() - window.startTime;
                remainingMs = elapsed >= windowMs ? windowMs : windowMs - elapsed;
            }
        }
        // Never zero: a Retry-After of 0 invites the immediate retry the limit exists to
        // refuse. Rounded up for the same reason - answering "1" for 1.4 seconds left sends
        // the caller back before the window has actually reopened.
        return Math.max(1, (remainingMs + 999) / 1000);
    }

    /**
     * Removes the keys whose window has closed: from that moment they count for nothing, and
     * keeping them around would be memory and nothing else.
     *
     * Visible to the tests on purpose: the cleanup is this class's reason to exist, and it
     * has to be checkable without waiting for it to happen on its own.
     */
    void purgeExpired(long now) {
        int before = windows.size();
        windows.entrySet().removeIf(entry -> {
            Window w = entry.getValue();
            synchronized (w) {
                return now - w.startTime > windowMs;
            }
        });
        int removed = before - windows.size();
        if (removed > 0) {
            logger.debug("Login limiter: removed {} expired keys, {} left", removed, windows.size());
        }
    }

    /** How many keys are in memory right now. Used by the tests, and by a metric one day. */
    int trackedKeys() {
        return windows.size();
    }

    private void warnOccasionally(long now) {
        if (now - lastWarning > 60_000) {
            lastWarning = now;
            logger.warn("Login limiter at its cap of {} keys: attempts from new keys are no longer "
                    + "counted until the window frees up. If it does not come back down, this is a "
                    + "distributed attack and needs a defence upstream of the service.", maxKeys);
        }
    }

    private static final class Window {
        long startTime;
        int attempts;

        Window(long startTime) {
            this.startTime = startTime;
        }
    }
}
