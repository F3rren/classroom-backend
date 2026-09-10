package com.classroom.auth.service;

import com.classroom.auth.model.RefreshToken;
import com.classroom.auth.repository.RefreshTokenRepository;
import com.classroom.exception.AuthenticationFailedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Issuing, rotating and revoking refresh tokens - the one piece of server-side state the
 * access token itself does not need, and does not get: JwtVerifier keeps verifying it
 * offline, in every service, exactly as before. Only this service, which already owns the
 * database the token issuer needs, also owns this one.
 *
 * A dedicated component rather than folded into AuthService, the same reasoning as
 * LoginAttemptLimiter: its own constructor-injected @Value settings instead of a static
 * default, so a test can build one with whatever expiry it needs without waiting for it.
 *
 * ONLY THE HASH IS EVER STORED. The raw value is a 256-bit random string - generated once,
 * returned once, never written down anywhere after that. A database leak hands out nothing
 * usable, the same property that keeps a user's password stored as a BCrypt hash rather than
 * in clear; SHA-256 is enough here specifically because the input already has 256 bits of
 * entropy chosen by SecureRandom, unlike a password, which needs a slow hash BECAUSE a human
 * did not.
 */
@Service
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RefreshTokenRepository repository;
    private final long expirationMs;

    public RefreshTokenService(
            RefreshTokenRepository repository,
            @Value("${jwt.refresh-token-expiration-ms:2592000000}") long expirationMs) {
        this.repository = repository;
        this.expirationMs = expirationMs;
    }

    /** The outcome of a successful rotation: who it belonged to, and the token that replaces it. */
    public record Rotation(Long userId, String refreshToken) {
    }

    /** Issues a brand new refresh token for a user - at login, and again on every rotation. */
    @Transactional
    public String issue(Long userId) {
        String raw = generateRawToken();

        RefreshToken entity = new RefreshToken();
        entity.setUserId(userId);
        entity.setTokenHash(hash(raw));
        entity.setCreatedAt(LocalDateTime.now());
        entity.setExpiresAt(LocalDateTime.now().plus(Duration.ofMillis(expirationMs)));
        repository.save(entity);

        return raw;
    }

    /**
     * Exchanges a refresh token for a new one, revoking the one presented in the same
     * transaction. Rotation, not reuse: a refresh token is good for exactly one refresh, so
     * that a stolen-and-later-replayed token is refused instead of quietly accepted alongside
     * the legitimate caller's.
     *
     * Refuses anything not found, already revoked, or past its own expiry - the three states
     * are not told apart in the response on purpose: a caller does not get to learn WHY a
     * token failed, only that it did, the same way login does not confirm which of email or
     * password was wrong.
     */
    @Transactional
    public Rotation rotate(String rawToken) {
        RefreshToken existing = repository.findByTokenHash(hash(rawToken)).orElse(null);

        if (existing == null || existing.getRevokedAt() != null
                || existing.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new AuthenticationFailedException("INVALID_REFRESH_TOKEN",
                    "Invalid, expired or already-used refresh token",
                    "La sessione e' scaduta. Effettua di nuovo il login.");
        }

        existing.setRevokedAt(LocalDateTime.now());
        repository.save(existing);

        String newRaw = issue(existing.getUserId());
        return new Rotation(existing.getUserId(), newRaw);
    }

    /**
     * Revokes a refresh token - logout. Silent and idempotent for anything not found or
     * already revoked: logout does not tell a caller whether the token it was given ever
     * existed, and calling it twice (a retry after a lost response, say) must not become an
     * error.
     */
    @Transactional
    public void revoke(String rawToken) {
        repository.findByTokenHash(hash(rawToken)).ifPresent(token -> {
            if (token.getRevokedAt() == null) {
                token.setRevokedAt(LocalDateTime.now());
                repository.save(token);
            }
        });
    }

    private static String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by every JDK distribution (JLS/JCA requirement): this is
            // not a condition production can reach, only a checked exception Java insists on
            // declaring anyway.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
