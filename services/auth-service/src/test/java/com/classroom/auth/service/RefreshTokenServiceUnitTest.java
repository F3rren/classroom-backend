package com.classroom.auth.service;

import com.classroom.auth.model.RefreshToken;
import com.classroom.auth.repository.RefreshTokenRepository;
import com.classroom.exception.AuthenticationFailedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * issue/rotate/revoke, mocking RefreshTokenRepository directly - same manual-mock style as
 * AuthServiceUnitTest, no Spring context needed for what is, underneath the hashing, a small
 * amount of bookkeeping over one repository.
 */
class RefreshTokenServiceUnitTest {

    private RefreshTokenRepository repository;
    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        repository = mock(RefreshTokenRepository.class);
        // 1 hour, so expiry-related tests below can use "created a moment ago" fixtures
        // without the token already being expired.
        service = new RefreshTokenService(repository, 3_600_000L);
        // save() is asserted on by argument capture in most tests below; where it is not,
        // returning the argument keeps this stub harmless everywhere it is unused.
        when(repository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private RefreshToken storedTokenFor(String hash, Long userId, LocalDateTime expiresAt, LocalDateTime revokedAt) {
        RefreshToken t = new RefreshToken();
        t.setId(1L);
        t.setUserId(userId);
        t.setTokenHash(hash);
        t.setCreatedAt(LocalDateTime.now());
        t.setExpiresAt(expiresAt);
        t.setRevokedAt(revokedAt);
        return t;
    }

    // ==================== issue ====================

    @Test
    void issueReturnsARawTokenAndPersistsOnlyItsHash() {
        String raw = service.issue(42L);

        assertThat(raw).isNotBlank();

        var captor = org.mockito.ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(captor.capture());
        RefreshToken saved = captor.getValue();

        assertThat(saved.getUserId()).isEqualTo(42L);
        assertThat(saved.getTokenHash()).isNotEqualTo(raw);
        // SHA-256 as a hex string is exactly 64 characters, regardless of input.
        assertThat(saved.getTokenHash()).hasSize(64);
        assertThat(saved.getExpiresAt()).isAfter(saved.getCreatedAt());
        assertThat(saved.getRevokedAt()).isNull();
    }

    @Test
    void twoIssuedTokensAreNeverTheSame() {
        // Not a mathematical proof SecureRandom cannot collide - just the cheapest possible
        // check that generateRawToken() is not, say, returning a constant by accident.
        assertThat(service.issue(1L)).isNotEqualTo(service.issue(1L));
    }

    // ==================== rotate ====================

    @Test
    void rotateRefusesATokenThatWasNeverIssued() {
        when(repository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rotate("mai-esistito"))
                .isInstanceOf(AuthenticationFailedException.class)
                .satisfies(e -> assertThat(((AuthenticationFailedException) e).getErrorCode())
                        .isEqualTo("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void rotateRefusesAnExpiredToken() {
        // The hash stored does not have to match hash("scaduto") for this test - the
        // repository is mocked to return this row regardless of what was looked up.
        RefreshToken expired = storedTokenFor("qualsiasi-hash", 7L,
                LocalDateTime.now().minusMinutes(1), null);
        when(repository.findByTokenHash(any())).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.rotate("scaduto"))
                .isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    void rotateRefusesAnAlreadyRevokedToken() {
        RefreshToken revoked = storedTokenFor("qualsiasi-hash", 7L,
                LocalDateTime.now().plusDays(1), LocalDateTime.now().minusMinutes(1));
        when(repository.findByTokenHash(any())).thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> service.rotate("gia-usato"))
                .isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    void rotateRevokesTheOldTokenAndIssuesANewOneForTheSameUser() {
        RefreshToken valid = storedTokenFor("qualsiasi-hash", 7L,
                LocalDateTime.now().plusDays(1), null);
        when(repository.findByTokenHash(any())).thenReturn(Optional.of(valid));

        RefreshTokenService.Rotation rotation = service.rotate("valido");

        assertThat(rotation.userId()).isEqualTo(7L);
        assertThat(rotation.refreshToken()).isNotBlank();
        assertThat(rotation.refreshToken()).isNotEqualTo("valido");

        // saved twice: once to revoke the presented token, once to persist the new one
        // (issue(), called internally) - both real writes, not one disguising the other.
        assertThat(valid.getRevokedAt()).isNotNull();
        verify(repository, times(2)).save(any(RefreshToken.class));
    }

    @Test
    void aTokenCannotBeRotatedTwice() {
        // Simulates the row AFTER a first rotate(): revoked, exactly as
        // rotateRevokesTheOldTokenAndIssuesANewOneForTheSameUser above just checked it
        // becomes. Presenting it again must be refused like any other used-up token.
        RefreshToken alreadyRotated = storedTokenFor("qualsiasi-hash", 7L,
                LocalDateTime.now().plusDays(1), LocalDateTime.now());
        when(repository.findByTokenHash(any())).thenReturn(Optional.of(alreadyRotated));

        assertThatThrownBy(() -> service.rotate("valido-ma-gia-ruotato"))
                .isInstanceOf(AuthenticationFailedException.class);
    }

    // ==================== revoke ====================

    @Test
    void revokeMarksAnExistingTokenAsRevoked() {
        RefreshToken existing = storedTokenFor("qualsiasi-hash", 3L,
                LocalDateTime.now().plusDays(1), null);
        when(repository.findByTokenHash(any())).thenReturn(Optional.of(existing));

        service.revoke("da-revocare");

        assertThat(existing.getRevokedAt()).isNotNull();
        verify(repository).save(existing);
    }

    @Test
    void revokeIsSilentForATokenThatWasNeverIssued() {
        // Logout must not tell a caller whether the token it was given ever existed - see
        // the method's own javadoc - so an unknown token is not an error of any kind.
        when(repository.findByTokenHash(any())).thenReturn(Optional.empty());

        service.revoke("sconosciuto");

        verify(repository, never()).save(any(RefreshToken.class));
    }

    @Test
    void revokeIsIdempotentOnAnAlreadyRevokedToken() {
        LocalDateTime firstRevocation = LocalDateTime.now().minusHours(1);
        RefreshToken alreadyRevoked = storedTokenFor("qualsiasi-hash", 3L,
                LocalDateTime.now().plusDays(1), firstRevocation);
        when(repository.findByTokenHash(any())).thenReturn(Optional.of(alreadyRevoked));

        service.revoke("gia-revocato");

        // The revocation timestamp is not overwritten, and no second save happens: revoking
        // twice must not look like two separate events.
        assertThat(alreadyRevoked.getRevokedAt()).isEqualTo(firstRevocation);
        verify(repository, never()).save(any(RefreshToken.class));
    }
}
