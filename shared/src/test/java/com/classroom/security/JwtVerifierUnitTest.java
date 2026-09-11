package com.classroom.security;

import com.classroom.testsupport.TestJwt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Token verification is the pivot of the service architecture: it is what lets every service
 * know who is calling without asking anybody. While it lived in
 * the application module it was exercised indirectly by the integration tests; moved into
 * shared it was left
 * without a single test, and this is security code.
 *
 * The case that matters most is a token signed with a different secret: if that got through,
 * anybody could manufacture an identity with whatever role they liked.
 */
class JwtVerifierUnitTest {

    private JwtVerifier verifier;

    @BeforeEach
    void setUp() {
        verifier = new JwtVerifier(TestJwt.TEST_SECRET);
    }

    @Test
    void acceptsATokenSignedWithTheSharedSecret() {
        String token = TestJwt.forUser(7L, "mario.rossi@example.it");

        assertThat(verifier.validateToken(token)).isTrue();
        assertThat(verifier.getUserIdFromToken(token)).isEqualTo(7L);
        assertThat(verifier.getEmailFromToken(token)).isEqualTo("mario.rossi@example.it");
        assertThat(verifier.getRoleFromToken(token)).isEqualTo("user");
    }

    @Test
    void itReadsTheAdminRoleFromTheClaim() {
        String token = TestJwt.forAdmin(1L, "admin@example.it");

        assertThat(verifier.getRoleFromToken(token)).isEqualTo("admin");
    }

    @Test
    void rejectsATokenSignedWithAnotherSecret() {
        // A different secret, the same shape: this is the attempt to forge an identity
        JwtVerifier anotherService = new JwtVerifier(
                "dW4tc2VncmV0by1jb21wbGV0YW1lbnRlLWRpdmVyc28tZGEtcXVlbGxvLXZlcm8");

        assertThat(verifier.validateToken(TestJwt.forAdmin(1L, "intruso@example.it"))).isTrue();
        assertThat(anotherService.validateToken(TestJwt.forAdmin(1L, "intruso@example.it"))).isFalse();
    }

    @Test
    void rejectsATamperedSignature() {
        String token = TestJwt.forUser(7L, "mario@example.it");
        String tampered = token.substring(0, token.lastIndexOf('.')) + ".firmaAlterata";

        assertThat(verifier.validateToken(tampered)).isFalse();
    }

    @Test
    void itRejectsAnExpiredToken() {
        assertThat(verifier.validateToken(TestJwt.expired(7L, "mario@example.it"))).isFalse();
    }

    @Test
    void rejectsGarbageWithoutThrowing() {
        // The filter calls validateToken on whatever arrives in the header: it has to
        // answer false, not propagate an exception.
        assertThat(verifier.validateToken("non-e-un-token")).isFalse();
        assertThat(verifier.validateToken("")).isFalse();
    }

    @Test
    void readsTheNameFromItsOwnClaim() {
        // The name travels in the token precisely so booking-service does not have to ask
        // auth-service who is booking.
        String token = TestJwt.forUser(7L, "mario.rossi@example.it", "Mario Rossi");

        assertThat(verifier.getNameFromToken(token)).isEqualTo("Mario Rossi");
    }

    @Test
    void aTokenIssuedBeforeTheNameClaimBreaksNothing() {
        // Tokens already in circulation have no such claim: they have to stay valid, and the
        // name simply has to be absent rather than blowing the filter up.
        String oldToken = TestJwt.withoutName(7L, "mario.rossi@example.it");

        assertThat(verifier.validateToken(oldToken)).isTrue();
        assertThat(verifier.getNameFromToken(oldToken)).isNull();
        assertThat(verifier.getUserIdFromToken(oldToken)).isEqualTo(7L);
    }
}
