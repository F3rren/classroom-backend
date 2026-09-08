package com.classroom.testsupport;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * Signs test tokens, with no user and no authentication service in sight.
 *
 * This is what makes the split into services bearable on the test side. Every integration
 * class used to create a real user and call /api/auth/login just to obtain a token: a
 * dependency on the user domain that had nothing to do with what the test wanted to check.
 * Eight classes out of ten crossed three or four domains for that reason alone.
 *
 * It works because validation is offline: JwtVerifier checks the signature and reads the
 * claims without ever consulting a database. A token signed here with the same secret is
 * indistinguishable from one issued by auth-service.
 *
 * It lives in shared's test sources and is published as a test-jar, so the other modules can
 * use it without it ever reaching the production artefact.
 */
public final class TestJwt {

    /** Must match the jwt.secret of the test profiles. */
    public static final String TEST_SECRET = "dGVzdC1zZWNyZXQtcGVyLWktdGVzdC1kaS1pbnRlZ3JhemlvbmUtMDAxMg";

    private static final long LIFETIME_MS = 1000L * 60 * 60;

    private TestJwt() {
    }

    /** A token for an ordinary user. The name is derived from the email. */
    public static String forUser(Long id, String email) {
        return sign(id, email, nameFrom(email), "user");
    }

    /** A token for a user whose name matters: it ends up in the room details. */
    public static String forUser(Long id, String email, String name) {
        return sign(id, email, name, "user");
    }

    /** A token for an administrator. */
    public static String forAdmin(Long id, String email) {
        return sign(id, email, nameFrom(email), "admin");
    }

    /** An already expired token, to check that it is refused. */
    public static String expired(Long id, String email) {
        return build(id, email, nameFrom(email), "user", new Date(System.currentTimeMillis() - LIFETIME_MS));
    }

    /** A token with no "name" claim: it mimics one issued before that claim existed. */
    public static String withoutName(Long id, String email) {
        return build(id, email, null, "user", new Date(System.currentTimeMillis() + LIFETIME_MS));
    }

    private static String nameFrom(String email) {
        return email == null ? null : email.split("@")[0];
    }

    private static String sign(Long id, String email, String name, String role) {
        return build(id, email, name, role, new Date(System.currentTimeMillis() + LIFETIME_MS));
    }

    private static String build(Long id, String email, String name, String role, Date expiry) {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64URL.decode(TEST_SECRET));
        return Jwts.builder()
                .subject(email)
                .claim("id", id)
                .claim("name", name)
                .claim("username", name == null ? null : name.toLowerCase().replace(" ", "."))
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(expiry)
                .signWith(key)
                .compact();
    }
}
