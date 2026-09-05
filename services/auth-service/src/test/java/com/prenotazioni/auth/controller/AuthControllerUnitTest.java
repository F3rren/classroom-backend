package com.prenotazioni.auth.controller;

import com.prenotazioni.dto.ApiEnvelope;
import com.prenotazioni.auth.dto.LoginRequest;
import com.prenotazioni.auth.dto.LoginResponse;
import com.prenotazioni.auth.model.User;
import com.prenotazioni.model.Role;
import com.prenotazioni.auth.service.AuthService;
import com.prenotazioni.auth.service.LoginAttemptLimiter;
import com.prenotazioni.auth.service.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;

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
    private LoginAttemptLimiter attemptLimiter;
    private HttpServletRequest httpRequest;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        authService = mock(AuthService.class);
        jwtService = mock(JwtService.class);
        // Il limitatore e' un componente a se': si costruisce con i suoi parametri invece
        // di iniettarli per riflessione, e ogni test ne ha uno pulito. Prima il contatore
        // era static e andava azzerato a mano fra un caso e l'altro, perche' surefire
        // riusa la JVM fra i contesti.
        attemptLimiter = new LoginAttemptLimiter(100, 60_000L, 1000);
        controller = new AuthController(authService, jwtService, attemptLimiter);

        httpRequest = mock(HttpServletRequest.class);
        when(httpRequest.getRemoteAddr()).thenReturn("10.0.0.1");
    }

    private LoginRequest credenziali(String email, String password) {
        LoginRequest r = new LoginRequest();
        r.setEmail(email);
        r.setPassword(password);
        return r;
    }

    private User utenteValido() {
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
        return ((ApiEnvelope<Object>) resp.getBody()).getError();
    }

    // ==================== rate limiting ====================

    @Test
    void blocksWithTooManyRequestsOnceTheAttemptLimitIsExceeded() {
        controller = new AuthController(authService, jwtService,
                new LoginAttemptLimiter(1, 60_000L, 1000));
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
        // finestra negativa: ogni chiamata risulta fuori finestra, quindi il contatore
        // riparte. Ora e' un parametro del costruttore invece di un campo da forzare.
        controller = new AuthController(authService, jwtService,
                new LoginAttemptLimiter(1, -1L, 1000));
        when(authService.login(anyString(), anyString())).thenReturn(null);

        controller.login(credenziali("u@test.it", "sbagliata"), httpRequest);
        ResponseEntity<?> secondo = controller.login(credenziali("u@test.it", "sbagliata"), httpRequest);

        // niente 429: la finestra e' scaduta e il contatore e' stato azzerato
        assertThat(secondo.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rateLimitIsPerEmailNotGlobal() {
        controller = new AuthController(authService, jwtService,
                new LoginAttemptLimiter(1, 60_000L, 1000));
        when(authService.login(anyString(), anyString())).thenReturn(null);

        controller.login(credenziali("primo@test.it", "password"), httpRequest);
        controller.login(credenziali("primo@test.it", "password"), httpRequest); // primo utente in 429

        ResponseEntity<?> altro = controller.login(credenziali("secondo@test.it", "password"), httpRequest);
        assertThat(altro.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ==================== validazione ====================

    @Test
    void rifiutaUnaEmailMancante() {
        ResponseEntity<?> resp = controller.login(credenziali(null, "password"), httpRequest);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(errorCode(resp)).isEqualTo("MISSING_EMAIL");
    }

    @Test
    void rifiutaUnaEmailMalformata() {
        ResponseEntity<?> resp = controller.login(credenziali("non-una-email", "password"), httpRequest);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(errorCode(resp)).isEqualTo("INVALID_EMAIL_FORMAT");
    }

    @Test
    void rifiutaUnaPasswordMancante() {
        ResponseEntity<?> resp = controller.login(credenziali("u@test.it", ""), httpRequest);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(errorCode(resp)).isEqualTo("MISSING_PASSWORD");
    }

    @Test
    void rifiutaUnaPasswordTroppoCorta() {
        ResponseEntity<?> resp = controller.login(credenziali("u@test.it", "ab"), httpRequest);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(errorCode(resp)).isEqualTo("PASSWORD_TOO_SHORT");
    }

    @Test
    void rifiutaCredenzialiSbagliate() {
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
        User corrotto = utenteValido();
        corrotto.setId(null);
        when(authService.login(anyString(), anyString())).thenReturn(corrotto);

        ResponseEntity<?> resp = controller.login(credenziali("u@test.it", "password"), httpRequest);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(errorCode(resp)).isEqualTo("USER_DATA_CORRUPTION");
    }

    @Test
    void risponde500SeIlTokenGeneratoEVuoto() {
        when(authService.login(anyString(), anyString())).thenReturn(utenteValido());
        when(jwtService.generateToken(any())).thenReturn("   ");

        ResponseEntity<?> resp = controller.login(credenziali("u@test.it", "password"), httpRequest);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(errorCode(resp)).isEqualTo("TOKEN_GENERATION_FAILED");
    }

    @Test
    void risponde500SeLaGenerazioneDelTokenFallisce() {
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
