package com.classroom.auth.controller;

import com.classroom.auth.dto.LoginRequest;
import com.classroom.auth.dto.LoginResponse;
import com.classroom.auth.model.User;
import com.classroom.model.Role;
import com.classroom.auth.service.AuthService;
import com.classroom.auth.service.LoginAttemptLimiter;
import com.classroom.auth.service.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.Objects;

import com.classroom.exception.ApplicationException;
import com.classroom.exception.AuthenticationFailedException;
import com.classroom.exception.InvalidRequestException;
import com.classroom.exception.TooManyRequestsException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for AuthController.
 *
 * The rate limiter is anti brute-force protection but was not covered: in the HTTP tests
 * application-test.properties raises max-attempts to 1000 precisely so it does not trip,
 * and it cannot be lowered there without sending the other classes into 429.
 *
 * The internal-error branches of the login are covered too, which over HTTP would require
 * breaking the service or the token generator.
 */
class AuthControllerUnitTest {

    private AuthService authService;
    private JwtService jwtService;
    private AuthController controller;
    private LoginAttemptLimiter attemptLimiter;
    private HttpServletRequest httpRequest;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        jwtService = mock(JwtService.class);
        // The limiter is a component of its own: it is built with its parameters instead of
        // having them injected by reflection, and every test gets a clean one. The counter
        // used to be static and had to be cleared by hand between cases, because surefire
        // reuses the JVM across contexts.
        attemptLimiter = new LoginAttemptLimiter(100, 60_000L, 1000);
        controller = new AuthController(authService, jwtService, attemptLimiter);

        httpRequest = mock(HttpServletRequest.class);
        when(httpRequest.getRemoteAddr()).thenReturn("10.0.0.1");
    }

    private LoginRequest credentials(String email, String password) {
        LoginRequest r = new LoginRequest();
        r.setEmail(email);
        r.setPassword(password);
        return r;
    }

    private User validUser() {
        User u = new User();
        u.setId(1L);
        u.setEmail("u@test.it");
        u.setUsername("utente");
        u.setName("Utente Test");
        u.setRole(Role.USER);
        u.setRegisteredAt(LocalDateTime.now());
        return u;
    }

    /**
     * The controller no longer builds error responses: it throws, and
     * GlobalExceptionHandler decides the status once. Called directly, as here, the
     * exception is what comes out - and the status each type becomes is pinned next to the
     * handler, in shared.
     */
    private void assertRefusedWith(Class<? extends ApplicationException> type,
                                   String expectedCode, ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(type)
                .satisfies(e -> assertThat(((ApplicationException) e).getErrorCode())
                        .isEqualTo(expectedCode));
    }

    // ==================== rate limiting ====================

    @Test
    void blocksWithTooManyRequestsOnceTheAttemptLimitIsExceeded() {
        controller = new AuthController(authService, jwtService,
                new LoginAttemptLimiter(1, 60_000L, 1000));
        when(authService.login(anyString(), anyString())).thenReturn(null);

        // first attempt: uses up the quota and fails on wrong credentials
        assertRefusedWith(AuthenticationFailedException.class, "INVALID_CREDENTIALS",
                () -> controller.login(credentials("u@test.it", "sbagliata"), httpRequest));

        // second attempt: over the threshold
        TooManyRequestsException refusal = catchThrowableOfType(
                () -> controller.login(credentials("u@test.it", "sbagliata"), httpRequest),
                TooManyRequestsException.class);

        assertThat(refusal.getErrorCode()).isEqualTo("TOO_MANY_ATTEMPTS");
        // The delay travels on the exception because only the limiter knows it. The handler
        // turns it into Retry-After, which is what makes the refusal actionable: the
        // userMessage says "tra qualche minuto" to a person, the header says how many
        // seconds to the code. RFC 9110 section 10.2.3.
        assertThat(refusal.getRetryAfterSeconds()).isBetween(1L, 60L);
    }

    @Test
    void countersResetAfterTheWindowExpires() {
        // A negative window: every call lands outside it, so the counter starts over. It is
        // a constructor parameter now, instead of a field to force by reflection.
        controller = new AuthController(authService, jwtService,
                new LoginAttemptLimiter(1, -1L, 1000));
        when(authService.login(anyString(), anyString())).thenReturn(null);

        assertRefusedWith(AuthenticationFailedException.class, "INVALID_CREDENTIALS",
                () -> controller.login(credentials("u@test.it", "sbagliata"), httpRequest));

        // no 429: the window has expired and the counter was reset, so the second attempt
        // is refused on the credentials again rather than on the limit
        assertRefusedWith(AuthenticationFailedException.class, "INVALID_CREDENTIALS",
                () -> controller.login(credentials("u@test.it", "sbagliata"), httpRequest));
    }

    @Test
    void rateLimitIsPerEmailNotGlobal() {
        controller = new AuthController(authService, jwtService,
                new LoginAttemptLimiter(1, 60_000L, 1000));
        when(authService.login(anyString(), anyString())).thenReturn(null);

        assertRefusedWith(AuthenticationFailedException.class, "INVALID_CREDENTIALS",
                () -> controller.login(credentials("primo@test.it", "password"), httpRequest));
        assertRefusedWith(TooManyRequestsException.class, "TOO_MANY_ATTEMPTS",
                () -> controller.login(credentials("primo@test.it", "password"), httpRequest));

        // a different email has a counter of its own
        assertRefusedWith(AuthenticationFailedException.class, "INVALID_CREDENTIALS",
                () -> controller.login(credentials("secondo@test.it", "password"), httpRequest));
    }

    // ==================== validation ====================

    @Test
    void rejectsAMissingEmail() {
        assertRefusedWith(InvalidRequestException.class, "MISSING_EMAIL",
                () -> controller.login(credentials(null, "password"), httpRequest));
    }

    @Test
    void rejectsAMalformedEmail() {
        assertRefusedWith(InvalidRequestException.class, "INVALID_EMAIL_FORMAT",
                () -> controller.login(credentials("non-una-email", "password"), httpRequest));
    }

    @Test
    void rejectsAMissingPassword() {
        assertRefusedWith(InvalidRequestException.class, "MISSING_PASSWORD",
                () -> controller.login(credentials("u@test.it", ""), httpRequest));
    }

    @Test
    void rejectsATooShortPassword() {
        assertRefusedWith(InvalidRequestException.class, "PASSWORD_TOO_SHORT",
                () -> controller.login(credentials("u@test.it", "ab"), httpRequest));
    }

    @Test
    void rejectsWrongCredentials() {
        when(authService.login(anyString(), anyString())).thenReturn(null);

        assertRefusedWith(AuthenticationFailedException.class, "INVALID_CREDENTIALS",
                () -> controller.login(credentials("u@test.it", "sbagliata"), httpRequest));
    }

    // ==================== internal errors ====================

    @Test
    void aFailureInTheServiceIsNotSwallowed() {
        when(authService.login(anyString(), anyString())).thenThrow(new RuntimeException("DB giu'"));

        // It used to be caught here and answered 500 AUTHENTICATION_ERROR. That catch took
        // Exception, so it also flattened what the handler maps properly - a database
        // constraint violation, for one, which is a 409. Now it rises: handleGeneric answers
        // 500 for a genuine unknown, and anything with a handler of its own reaches it.
        assertThatThrownBy(() -> controller.login(credentials("u@test.it", "password"), httpRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB giu'");
    }

    @Test
    void noTokenIsIssuedForAUserWithoutAnId() {
        User corrupted = validUser();
        corrupted.setId(null);
        when(authService.login(anyString(), anyString())).thenReturn(corrupted);

        // IllegalStateException and not a hand-built 500: this is a state the database cannot
        // produce - the id is a primary key - so it is a defect of ours, and this codebase
        // answers those with the generic 500 rather than dressing them up as the caller's
        // fault. The check stays because it guards the moment a token is handed out.
        assertThatThrownBy(() -> controller.login(credentials("u@test.it", "password"), httpRequest))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void anEmptyTokenIsNeverHandedOut() {
        when(authService.login(anyString(), anyString())).thenReturn(validUser());
        when(jwtService.generateToken(any())).thenReturn("   ");

        assertThatThrownBy(() -> controller.login(credentials("u@test.it", "password"), httpRequest))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aFailureGeneratingTheTokenIsNotSwallowedEither() {
        when(authService.login(anyString(), anyString())).thenReturn(validUser());
        when(jwtService.generateToken(any())).thenThrow(new IllegalStateException("chiave assente"));

        assertThatThrownBy(() -> controller.login(credentials("u@test.it", "password"), httpRequest))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("chiave assente");
    }

    @Test
    void returns200WithATokenOnSuccess() {
        when(authService.login(anyString(), anyString())).thenReturn(validUser());
        when(jwtService.generateToken(any())).thenReturn("token-valido");

        ResponseEntity<?> resp = controller.login(credentials("u@test.it", "password"), httpRequest);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        LoginResponse body = (LoginResponse) Objects.requireNonNull(resp.getBody());
        assertThat(body.getToken()).isEqualTo("token-valido");
        // the token is duplicated inside "data" too, the historic shape the frontend expects
        assertThat(body.getData().getToken()).isEqualTo("token-valido");
        assertThat(body.isSuccess()).isTrue();
    }

    private static <T> T any() {
        return org.mockito.ArgumentMatchers.any();
    }
}
