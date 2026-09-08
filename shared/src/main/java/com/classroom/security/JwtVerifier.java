package com.classroom.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.function.Function;

/**
 * The read-only half of JwtService: it verifies the signature and reads the claims.
 *
 * This is the piece that makes the split into services cheap. Validating a token here
 * involves no database access and no call to auth-service: knowing the secret is enough.
 * Every service decides for itself who is calling and with which role, and as a result every
 * service's tests can sign their own tokens instead of having to create a real user.
 *
 * Issuing (generateToken) stays out of here, because it needs the User entity and belongs to
 * the one service that owns the users table.
 *
 * The trade-off worth keeping in mind: the secret is now shared between several processes,
 * so rotating it means restarting all of them together.
 */
@Component
public class JwtVerifier {

    private static final Logger logger = LoggerFactory.getLogger(JwtVerifier.class);

    @Value("${jwt.secret}")
    private String secret;

    private SecretKey key;

    @PostConstruct
    public void init() {
        // The secret is BASE64URL-encoded, not raw bytes: decoding it any other way would
        // produce a different key and every token would be refused.
        this.key = JwtKey.from(secret);
    }

    public boolean validateToken(String token) {
        try {
            extractAllClaims(token);
            return true;
        } catch (Exception e) {
            logger.warn("Validazione token fallita: {}", e.getMessage());
            return false;
        }
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        return claimsResolver.apply(extractAllClaims(token));
    }

    public String getEmailFromToken(String token) {
        return extractClaim(token, claims -> claims.getSubject());
    }

    /**
     * The user's display name.
     *
     * It sits in the token for a precise reason: it is the only piece of user data the
     * downstream services need (it ends up in the "user" field of the room details), and
     * carrying it here saves booking-service a network call to auth-service on every booking.
     * A token issued before this change has no such claim: it comes back null, and callers
     * have to handle that rather than assume it is there.
     */
    public String getUsernameFromToken(String token) {
        return extractClaim(token, claims -> claims.get("username", String.class));
    }

    public String getNameFromToken(String token) {
        return extractClaim(token, claims -> claims.get("name", String.class));
    }

    public String getRoleFromToken(String token) {
        return extractClaim(token, claims -> claims.get("role", String.class));
    }

    public Long getUserIdFromToken(String token) {
        return extractClaim(token, claims -> claims.get("id", Long.class));
    }
}
