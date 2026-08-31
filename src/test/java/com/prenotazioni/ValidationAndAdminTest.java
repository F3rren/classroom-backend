package com.prenotazioni;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prenotazioni.model.Utente;
import com.prenotazioni.repository.IAulaRepository;
import com.prenotazioni.repository.IPrenotazioneRepository;
import com.prenotazioni.repository.IUtenteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression suite per la Fase 2 del refactor Swagger: Bean Validation sui DTO
 * (CreateUserRequest/UpdateUserRequest/AulaRequest/PrenotazioneRequest) e conferma
 * che il comportamento di AuthController.login NON sia cambiato (nessun @Valid li',
 * deliberatamente, per non alterare l'ordine rispetto al rate limiter).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ValidationAndAdminTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private IUtenteRepository utenteRepository;

    @Autowired
    private IAulaRepository aulaRepository;

    @Autowired
    private IPrenotazioneRepository prenotazioneRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String tokenAdmin;
    private String tokenUser;

    @BeforeEach
    void setUp() {
        prenotazioneRepository.deleteAll();
        aulaRepository.deleteAll();
        utenteRepository.deleteAll();

        Utente admin = new Utente();
        admin.setEmail("admin@validation.test");
        admin.setUsername("admin-validation");
        admin.setPassword(passwordEncoder.encode("admin-password"));
        admin.setNome("Admin Validation");
        admin.setRuolo("admin");
        admin.setDataRegistrazione(LocalDateTime.now());
        utenteRepository.save(admin);

        Utente user = new Utente();
        user.setEmail("user@validation.test");
        user.setUsername("user-validation");
        user.setPassword(passwordEncoder.encode("user-password"));
        user.setNome("User Validation");
        user.setRuolo("user");
        user.setDataRegistrazione(LocalDateTime.now());
        utenteRepository.save(user);

        tokenAdmin = login("admin@validation.test", "admin-password");
        tokenUser = login("user@validation.test", "user-password");
    }

    @SuppressWarnings("unchecked")
    private String login(String email, String password) {
        Map<String, String> body = Map.of("email", email, "password", password);
        ResponseEntity<Map> resp = rest.postForEntity("/api/auth/login", body, Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) resp.getBody().get("token");
    }

    private HttpHeaders bearerJson(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(String json) throws Exception {
        return objectMapper.readValue(json, Map.class);
    }

    // ==================== CreateUserRequest ====================

    @Test
    void adminRegisterWithInvalidEmailIsRejectedByBeanValidation() throws Exception {
        Map<String, Object> body = Map.of(
                "username", "nuovoutente",
                "email", "non-e-una-email",
                "password", "password1234",
                "nome", "Nuovo Utente",
                "ruolo", "user");

        ResponseEntity<String> resp = rest.exchange(
                "/api/admin/register", HttpMethod.POST, new HttpEntity<>(body, bearerJson(tokenAdmin)), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Map<String, Object> responseBody = asMap(resp.getBody());
        assertThat(responseBody.get("error")).isEqualTo("VALIDATION_ERROR");
        assertThat(responseBody.get("success")).isEqualTo(false);
    }

    @Test
    void adminRegisterWithShortPasswordIsRejected() throws Exception {
        Map<String, Object> body = Map.of(
                "username", "nuovoutente2",
                "email", "nuovoutente2@validation.test",
                "password", "short",
                "nome", "Nuovo Utente 2",
                "ruolo", "user");

        ResponseEntity<String> resp = rest.exchange(
                "/api/admin/register", HttpMethod.POST, new HttpEntity<>(body, bearerJson(tokenAdmin)), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(asMap(resp.getBody()).get("error")).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void adminRegisterWithInvalidRuoloIsRejected() throws Exception {
        Map<String, Object> body = Map.of(
                "username", "nuovoutente3",
                "email", "nuovoutente3@validation.test",
                "password", "password1234",
                "nome", "Nuovo Utente 3",
                "ruolo", "superadmin");

        ResponseEntity<String> resp = rest.exchange(
                "/api/admin/register", HttpMethod.POST, new HttpEntity<>(body, bearerJson(tokenAdmin)), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(asMap(resp.getBody()).get("error")).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void adminRegisterWithValidDataSucceeds() throws Exception {
        Map<String, Object> body = Map.of(
                "username", "nuovoutente4",
                "email", "nuovoutente4@validation.test",
                "password", "password1234",
                "nome", "Nuovo Utente 4",
                "ruolo", "user");

        ResponseEntity<String> resp = rest.exchange(
                "/api/admin/register", HttpMethod.POST, new HttpEntity<>(body, bearerJson(tokenAdmin)), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(asMap(resp.getBody()).get("success")).isEqualTo(true);
    }

    @Test
    void nonAdminCannotRegisterUsers() {
        Map<String, Object> body = Map.of(
                "username", "x", "email", "x@validation.test", "password", "password1234", "nome", "X");

        ResponseEntity<String> resp = rest.exchange(
                "/api/admin/register", HttpMethod.POST, new HttpEntity<>(body, bearerJson(tokenUser)), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ==================== UpdateUserRequest ====================

    @Test
    void adminUpdateWithBlankPasswordKeepsExistingPassword() throws Exception {
        Utente target = utenteRepository.findByEmail("user@validation.test");

        Map<String, Object> body = Map.of(
                "username", "user-validation",
                "email", "user@validation.test",
                "password", "",
                "nome", "User Validation Rinominato",
                "ruolo", "user");

        ResponseEntity<String> resp = rest.exchange(
                "/api/admin/users/" + target.getId(), HttpMethod.PUT,
                new HttpEntity<>(body, bearerJson(tokenAdmin)), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);

        // La vecchia password deve continuare a funzionare
        login("user@validation.test", "user-password");
    }

    // ==================== AulaRequest ====================

    @Test
    void createRoomWithNegativeCapienzaIsRejected() throws Exception {
        Map<String, Object> body = Map.of("nome", "Aula X", "piano", 1, "capienza", -5);

        ResponseEntity<String> resp = rest.exchange(
                "/api/admin/createrooms", HttpMethod.POST, new HttpEntity<>(body, bearerJson(tokenAdmin)), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(asMap(resp.getBody()).get("error")).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void createRoomWithBlankNameIsRejected() throws Exception {
        Map<String, Object> body = Map.of("nome", "  ", "piano", 1, "capienza", 10);

        ResponseEntity<String> resp = rest.exchange(
                "/api/admin/createrooms", HttpMethod.POST, new HttpEntity<>(body, bearerJson(tokenAdmin)), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(asMap(resp.getBody()).get("error")).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void createRoomWithValidDataSucceeds() {
        Map<String, Object> body = Map.of("nome", "Aula Valida", "piano", 2, "capienza", 25);

        ResponseEntity<String> resp = rest.exchange(
                "/api/admin/createrooms", HttpMethod.POST, new HttpEntity<>(body, bearerJson(tokenAdmin)), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    // ==================== PrenotazioneRequest ====================

    @Test
    void bloccaAulaWithMissingAulaIdIsRejectedByBeanValidation() throws Exception {
        Map<String, Object> body = Map.of(
                "inizio", LocalDateTime.now().plusDays(1).toString(),
                "fine", LocalDateTime.now().plusDays(1).plusHours(1).toString());

        ResponseEntity<String> resp = rest.exchange(
                "/api/prenotazioni/blocca", HttpMethod.POST, new HttpEntity<>(body, bearerJson(tokenAdmin)), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(asMap(resp.getBody()).get("error")).isEqualTo("VALIDATION_ERROR");
    }

    // ==================== AuthController.login: comportamento invariato (nessun @Valid) ====================

    @Test
    void loginWithBlankEmailStillReturnsLegacyMissingEmailCode() throws Exception {
        Map<String, Object> body = Map.of("email", "", "password", "irrilevante");

        ResponseEntity<String> resp = rest.exchange(
                "/api/auth/login", HttpMethod.POST,
                new HttpEntity<>(body, jsonHeaders()), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        // Deve restituire ESATTAMENTE il codice legacy, non il generico VALIDATION_ERROR:
        // conferma che @Valid non e' stato aggiunto al login (altrimenti l'ordine con
        // il rate limiter cambierebbe).
        assertThat(asMap(resp.getBody()).get("error")).isEqualTo("MISSING_EMAIL");
    }

    @Test
    void loginWithBlankPasswordStillReturnsLegacyMissingPasswordCode() throws Exception {
        Map<String, Object> body = Map.of("email", "admin@validation.test", "password", "");

        ResponseEntity<String> resp = rest.exchange(
                "/api/auth/login", HttpMethod.POST,
                new HttpEntity<>(body, jsonHeaders()), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(asMap(resp.getBody()).get("error")).isEqualTo("MISSING_PASSWORD");
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    // ==================== Filtro di sicurezza: 401/403 ricchi anche senza dispatch al controller ====================

    @Test
    void protectedEndpointWithoutTokenReturnsRichJson401() throws Exception {
        ResponseEntity<String> resp = rest.exchange(
                "/api/rooms", HttpMethod.GET, HttpEntity.EMPTY, String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        Map<String, Object> body = asMap(resp.getBody());
        assertThat(body.get("success")).isEqualTo(false);
        assertThat(body.get("userMessage")).isNotNull();
    }
}
