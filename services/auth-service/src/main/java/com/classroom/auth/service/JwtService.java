package com.classroom.auth.service;

import com.classroom.security.JwtKey;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.classroom.auth.model.User;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * It issues tokens and nothing else: verification lives in shared/JwtVerifier, because
 * every service verifies while only the owner of the users table signs.
 *
 * No logger here: generateToken is a single pure step with no branch worth tracing, and
 * both its outcomes are already logged where they mean something - AuthController logs
 * the empty/failed case, and the exception a broken key would raise propagates up to be
 * logged there too.
 */
@Service
public class JwtService {

    private final SecretKey key;

    // Same 1-hour value as before this became a property: nobody's behaviour changes by
    // default. It is a property now, not a hardcoded field, specifically so it can be
    // shortened later without a code change - the natural next step once /api/auth/refresh
    // (RefreshTokenService) is confirmed working end to end, since a short access token plus
    // silent refresh is the point of having a refresh token at all.
    private final long expiration;

    public JwtService(@Value("${jwt.secret}") String secret,
                       @Value("${jwt.access-token-expiration-ms:3600000}") long expiration) {
        this.key = JwtKey.from(secret);
        this.expiration = expiration;
    }

    public String generateToken(User user) {
        return Jwts.builder()
                .subject(user.getEmail())
                .claim("id", user.getId())
                // booking-service needs this to store the name of whoever booked without
                // querying the user service: see JwtVerifier.getNameFromToken.
                .claim("name", user.getName())
                .claim("username", user.getUsername())
                .claim("role", user.getRole() != null ? user.getRole().getValue() : null)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(key)
                .compact();
    }
}
