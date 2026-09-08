package com.classroom.auth.controller;

import com.classroom.dto.ApiEnvelope;
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

import static org.assertj.core.api.Assertions.assertThat;
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

    @SuppressWarnings("unchecked")
    private String errorCode(ResponseEntity<?> resp) {
        return ((ApiEnvelope<Object>) Objects.requireNonNull(resp.getBody())).getError();
    }

    // ==================== rate limiting ====================

    @Test
    void blocksWithTooManyRequestsOnceTheAttemptLimitIsExceeded() {
        controller = new AuthController(authService, jwtService,
                new LoginAttemptLimiter(1, 60_000L, 1000));
        when(authService.login(anyString(), anyString())).thenReturn(null);

        // first attempt: uses up the quota and fails on wrong credentials
        ResponseEntity<?> first = controller.login(credentials("u@test.it", "sbagliata"), httpRequest);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // second attempt: over the threshold
        ResponseEntity<?> second = controller.login(credentials("u@test.it", "sbagliata"), httpRequest);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(errorCode(second)).isEqualTo("TOO_MANY_ATTEMPTS");
    }

    @Test
    void countersResetAfterTheWindowExpires() {
        // A negative window: every call lands outside it, so the counter starts over. It is
        // a constructor parameter now, instead of a field to force by reflection.
        controller = new AuthController(authService, jwtService,
                new LoginAttemptLimiter(1, -1L, 1000));
        when(authService.login(anyString(), anyString())).thenReturn(null);

        controller.login(credentials("u@test.it", "sbagliata"), httpRequest);
        ResponseEntity<?> second = controller.login(credentials("u@test.it", "sbagliata"), httpRequest);

        // no 429: the window has expired and the counter was reset
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rateLimitIsPerEmailNotGlobal() {
        controller = new AuthController(authService, jwtService,
                new LoginAttemptLimiter(1, 60_000L, 1000));
        when(authService.login(anyString(), anyString())).thenReturn(null);

        controller.login(credentials("primo@test.it", "password"), httpRequest);
        controller.login(credentials("primo@test.it", "password"), httpRequest); // primo utente in 429

        ResponseEntity<?> other = controller.login(credentials("secondo@test.it", "password"), httpRequest);
        assertThat(other.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ==================== validation ====================

    @Test
    void rejectsAMissingEmail() {
        ResponseEntity<?> resp = controller.login(credentials(null, "password"), httpRequest);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(errorCode(resp)).isEqualTo("MISSING_EMAIL");
    }

    @Test
    void rejectsAMalformedEmail() {
        ResponseEntity<?> resp = controller.login(credentials("non-una-email", "password"), httpRequest);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(errorCode(resp)).isEqualTo("INVALID_EMAIL_FORMAT");
    }

    @Test
    void rejectsAMissingPassword() {
        ResponseEntity<?> resp = controller.login(credentials("u@test.it", ""), httpRequest);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(errorCode(resp)).isEqualTo("MISSING_PASSWORD");
    }

    @Test
    void rejectsATooShortPassword() {
        ResponseEntity<?> resp = controller.login(credentials("u@test.it", "ab"), httpRequest);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(errorCode(resp)).isEqualTo("PASSWORD_TOO_SHORT");
    }

    @Test
    void rejectsWrongCredentials() {
        when(authService.login(anyString(), anyString())).thenReturn(null);

        ResponseEntity<?> resp = controller.login(credentials("u@test.it", "sbagliata"), httpRequest);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(errorCode(resp)).isEqualTo("INVALID_CREDENTIALS");
    }

    // ==================== internal errors ====================

    @Test
    void returns500WhenTheServiceBlowsUp() {
        when(authService.login(anyString(), anyString())).thenThrow(new RuntimeException("DB giu'"));

        ResponseEntity<?> resp = controller.login(credentials("u@test.it", "password"), httpRequest);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(errorCode(resp)).isEqualTo("AUTHENTICATION_ERROR");
    }

    @Test
    void returns500WhenTheUserHasNoId() {
        User corrupted = validUser();
        corrupted.setId(null);
        when(authService.login(anyString(), anyString())).thenReturn(corrupted);

        ResponseEntity<?> resp = controller.login(credentials("u@test.it", "password"), httpRequest);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(errorCode(resp)).isEqualTo("USER_DATA_CORRUPTION");
    }

    @Test
    void returns500WhenTheGeneratedTokenIsEmpty() {
        when(authService.login(anyString(), anyString())).thenReturn(validUser());
        when(jwtService.generateToken(any())).thenReturn("   ");

        ResponseEntity<?> resp = controller.login(credentials("u@test.it", "password"), httpRequest);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(errorCode(resp)).isEqualTo("TOKEN_GENERATION_FAILED");
    }

    @Test
    void returns500WhenTokenGenerationFails() {
        when(authService.login(anyString(), anyString())).thenReturn(validUser());
        when(jwtService.generateToken(any())).thenThrow(new IllegalStateException("chiave assente"));

        ResponseEntity<?> resp = controller.login(credentials("u@test.it", "password"), httpRequest);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(errorCode(resp)).isEqualTo("TOKEN_GENERATION_ERROR");
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
