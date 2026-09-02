package com.prenotazioni.auth.controller;

import com.prenotazioni.dto.ApiEnvelope;
import com.prenotazioni.auth.dto.LoginRequest;
import com.prenotazioni.auth.dto.LoginResponse;
import com.prenotazioni.auth.model.Utente;
import com.prenotazioni.model.Ruolo;
import com.prenotazioni.auth.service.AuthService;
import com.prenotazioni.auth.service.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test di AuthController.
 *
 * Il rate limiter e' protezione anti brute-force ma non era coperto: nei test HTTP
 * application-test.properties alza max-attempts a 1000 proprio per non farlo scattare,
 * e non si puo' abbassare li' senza mandare in 429 le altre classi (la mappa dei
 * tentativi e' static e surefire riusa la JVM fra i contesti).
 *
 * Coperti anche i rami di errore interno del login, che via HTTP richiederebbero di
 * rompere il service o il generatore di token.
 */
class AuthControllerUnitTest {

    private AuthService authService;
    private JwtService jwtService;
    private AuthController controller;
    private HttpServletRequest httpRequest;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        authService = mock(AuthService.class);
        jwtService = mock(JwtService.class);
        controller = new AuthController(authService, jwtService);

        // maxLoginAttempts e rateLimitWindowMs sono @Value: senza contesto Spring valgono 0,
        // e con max=0 ogni chiamata risulterebbe gia' oltre soglia.
        ReflectionTestUtils.setField(controller, "maxLoginAttempts", 100);
        ReflectionTestUtils.setField(controller, "rateLimitWindowMs", 60000L);

        // la mappa dei tentativi e' static: va azzerata fra un test e l'altro
        ((Map<?, ?>) ReflectionTestUtils.getField(AuthController.class, "loginAttempts")).clear();

        httpRequest = mock(HttpServletRequest.class);
        when(httpRequest.getRemoteAddr()).thenReturn("10.0.0.1");
    }

    private LoginRequest credenziali(String email, String password) {
        LoginRequest r = new LoginRequest();
        r.setEmail(email);
        r.setPassword(password);
        return r;
    }

    private Utente utenteValido() {
        Utente u = new Utente();
        u.setId(1L);
        u.setEmail("u@test.it");
        u.setUsername("utente");
        u.setNome("Utente Test");
        u.setRuolo(Ruolo.USER);
        u.setDataRegistrazione(LocalDateTime.now());
        return u;
    }

    @SuppressWarnings("unchecked")
    private String errorCode(ResponseEntity<?> resp) {
        return ((ApiEnvelope<Object>) resp.getBody()).getError();
    }

    // ==================== rate limiting ====================

    @Test
    void blocksWithTooManyRequestsOnceTheAttemptLimitIsExceeded() {
        ReflectionTestUtils.setField(controller, "maxLoginAttempts", 1);
        when(authService.login(anyString(), anyString())).thenReturn(null);

        // primo tentativo: consuma la quota e fallisce per credenziali errate
        ResponseEntity<?> primo = controller.login(credenziali("u@test.it", "sbagliata"), httpRequest);
        assertThat(primo.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // secondo tentativo: oltre soglia
        ResponseEntity<?> secondo = controller.login(credenziali("u@test.it", "sbagliata"), httpRequest);
        assertThat(secondo.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(errorCode(secondo)).isEqualTo("TOO_MANY_ATTEMPTS");
    }

    @Test
    void countersResetAfterTheWindowExpires() {
        ReflectionTestUtils.setField(controller, "maxLoginAttempts", 1);
        // finestra negativa: ogni chiamata risulta fuori finestra, quindi il contatore riparte
        ReflectionTestUtils.setField(controller, "rateLimitWindowMs", -1L);
        when(authService.login(anyString(), anyString())).thenReturn(null);

        controller.login(credenziali("u@test.it", "sbagliata"), httpRequest);
        ResponseEntity<?> secondo = controller.login(credenziali("u@test.it", "sbagliata"), httpRequest);

        // niente 429: la finestra e' scaduta e il contatore e' stato azzerato
        assertThat(secondo.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rateLimitIsPerEmailNotGlobal() {
        ReflectionTestUtils.setField(controller, "maxLoginAttempts", 1);
        when(authService.login(anyString(), anyString())).thenReturn(null);

        controller.login(credenziali("primo@test.it", "password"), httpRequest);
        controller.login(credenziali("primo@test.it", "password"), httpRequest); // primo utente in 429

        ResponseEntity<?> altro = controller.login(credenziali("secondo@test.it", "password"), httpRequest);
        assertThat(altro.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ==================== validazione ====================

    @Test
    void rejectsMissingEmail() {
        ResponseEntity<?> resp = controller.login(credenziali(null, "password"), httpRequest);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(errorCode(resp)).isEqualTo("MISSING_EMAIL");
    }

    @Test
    void rejectsMalformedEmail() {
        ResponseEntity<?> resp = controller.login(credenziali("non-una-email", "password"), httpRequest);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(errorCode(resp)).isEqualTo("INVALID_EMAIL_FORMAT");
    }

    @Test
    void rejectsMissingPassword() {
        ResponseEntity<?> resp = controller.login(credenziali("u@test.it", ""), httpRequest);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(errorCode(resp)).isEqualTo("MISSING_PASSWORD");
    }

    @Test
    void rejectsTooShortPassword() {
        ResponseEntity<?> resp = controller.login(credenziali("u@test.it", "ab"), httpRequest);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(errorCode(resp)).isEqualTo("PASSWORD_TOO_SHORT");
    }

    @Test
    void rejectsWrongCredentials() {
        when(authService.login(anyString(), anyString())).thenReturn(null);

        ResponseEntity<?> resp = controller.login(credenziali("u@test.it", "sbagliata"), httpRequest);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(errorCode(resp)).isEqualTo("INVALID_CREDENTIALS");
    }

    // ==================== errori interni ====================

    @Test
    void returns500WhenTheServiceBlowsUp() {
        when(authService.login(anyString(), anyString())).thenThrow(new RuntimeException("DB giu'"));

        ResponseEntity<?> resp = controller.login(credenziali("u@test.it", "password"), httpRequest);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(errorCode(resp)).isEqualTo("AUTHENTICATION_ERROR");
    }

    @Test
    void returns500WhenTheUserHasNoId() {
        Utente corrotto = utenteValido();
        corrotto.setId(null);
        when(authService.login(anyString(), anyString())).thenReturn(corrotto);

        ResponseEntity<?> resp = controller.login(credenziali("u@test.it", "password"), httpRequest);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(errorCode(resp)).isEqualTo("USER_DATA_CORRUPTION");
    }

    @Test
    void returns500WhenTokenGenerationYieldsAnEmptyToken() {
        when(authService.login(anyString(), anyString())).thenReturn(utenteValido());
        when(jwtService.generateToken(any())).thenReturn("   ");

        ResponseEntity<?> resp = controller.login(credenziali("u@test.it", "password"), httpRequest);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(errorCode(resp)).isEqualTo("TOKEN_GENERATION_FAILED");
    }

    @Test
    void returns500WhenTokenGenerationThrows() {
        when(authService.login(anyString(), anyString())).thenReturn(utenteValido());
        when(jwtService.generateToken(any())).thenThrow(new IllegalStateException("chiave assente"));

        ResponseEntity<?> resp = controller.login(credenziali("u@test.it", "password"), httpRequest);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(errorCode(resp)).isEqualTo("TOKEN_GENERATION_ERROR");
    }

    @Test
    void returns200WithATokenOnSuccess() {
        when(authService.login(anyString(), anyString())).thenReturn(utenteValido());
        when(jwtService.generateToken(any())).thenReturn("token-valido");

        ResponseEntity<?> resp = controller.login(credenziali("u@test.it", "password"), httpRequest);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        LoginResponse body = (LoginResponse) resp.getBody();
        assertThat(body.getToken()).isEqualTo("token-valido");
        // il token e' duplicato anche dentro "data", shape storica attesa dal frontend
        assertThat(body.getData().getToken()).isEqualTo("token-valido");
        assertThat(body.isSuccess()).isTrue();
    }

    private static <T> T any() {
        return org.mockito.ArgumentMatchers.any();
    }
}
