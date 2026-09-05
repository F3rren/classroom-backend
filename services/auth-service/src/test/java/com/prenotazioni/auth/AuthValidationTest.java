package com.prenotazioni.auth;

import com.prenotazioni.testsupport.TestJson;
import com.prenotazioni.auth.model.User;
import com.prenotazioni.auth.repository.UserRepository;
import com.prenotazioni.model.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validazione di registrazione e login.
 *
 * Questi test stavano in ValidationAndAdminTest, nel modulo applicativo, insieme alla
 * validazione di aule e prenotazioni. Con la separazione hanno seguito gli endpoint che
 * verificano: /api/admin/users e /api/auth/login appartengono a questo servizio, e da
 * app rispondono 404.
 *
 * I due test sui codici "legacy" del login sono i piu' importanti del file: i controlli su
 * email e password vuote sono manuali e girano DOPO il rate limiter, quindi restituiscono
 * codici propri invece del VALIDATION_ERROR di Bean Validation. Vedi il javadoc di
 * LoginRequest per il perche' quell'ordine non va cambiato.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthValidationTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String tokenAdmin;
    private String tokenUser;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        salvaUtente("admin@validation.test", "admin-validation", "admin-password", Role.ADMIN);
        salvaUtente("user@validation.test", "user-validation", "user-password", Role.USER);

        tokenAdmin = login("admin@validation.test", "admin-password");
        tokenUser = login("user@validation.test", "user-password");
    }

    private void salvaUtente(String email, String username, String rawPassword, Role role) {
        User u = new User();
        u.setEmail(email);
        u.setUsername(username);
        u.setPassword(passwordEncoder.encode(rawPassword));
        u.setName(username);
        u.setRole(role);
        u.setRegisteredAt(LocalDateTime.now());
        userRepository.save(u);
    }

    @SuppressWarnings("unchecked")
    private String login(String email, String password) {
        ResponseEntity<Map> resp = rest.postForEntity(
                "/api/auth/login", Map.of("email", email, "password", password), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) resp.getBody().get("token");
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return h;
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return h;
    }

    private HttpHeaders bearerJson(String token) {
        HttpHeaders h = bearer(token);
        h.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return h;
    }


    @Test
    void emailNonValidaVieneRifiutataDallaValidazione() throws Exception {
        Map<String, Object> body = Map.of(
                "username", "nuovoutente",
                "email", "non-e-una-email",
                "password", "password1234",
                "name", "Nuovo Utente",
                "role", "user");

        ResponseEntity<String> resp = rest.exchange(
                "/api/admin/users", HttpMethod.POST, new HttpEntity<>(body, bearerJson(tokenAdmin)), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Map<String, Object> responseBody = TestJson.comeMappa(resp.getBody());
        assertThat(responseBody.get("error")).isEqualTo("VALIDATION_ERROR");
        assertThat(responseBody.get("success")).isEqualTo(false);
    }

    @Test
    void passwordTroppoCortaVieneRifiutata() throws Exception {
        Map<String, Object> body = Map.of(
                "username", "nuovoutente2",
                "email", "nuovoutente2@validation.test",
                "password", "short",
                "name", "Nuovo Utente 2",
                "role", "user");

        ResponseEntity<String> resp = rest.exchange(
                "/api/admin/users", HttpMethod.POST, new HttpEntity<>(body, bearerJson(tokenAdmin)), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(TestJson.comeMappa(resp.getBody()).get("error")).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void adminRegisterWithInvalidRuoloIsRejected() throws Exception {
        Map<String, Object> body = Map.of(
                "username", "nuovoutente3",
                "email", "nuovoutente3@validation.test",
                "password", "password1234",
                "name", "Nuovo Utente 3",
                "role", "superadmin");

        ResponseEntity<String> resp = rest.exchange(
                "/api/admin/users", HttpMethod.POST, new HttpEntity<>(body, bearerJson(tokenAdmin)), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(TestJson.comeMappa(resp.getBody()).get("error")).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void datiValidiCreanoLUtente() throws Exception {
        Map<String, Object> body = Map.of(
                "username", "nuovoutente4",
                "email", "nuovoutente4@validation.test",
                "password", "password1234",
                "name", "Nuovo Utente 4",
                "role", "user");

        ResponseEntity<String> resp = rest.exchange(
                "/api/admin/users", HttpMethod.POST, new HttpEntity<>(body, bearerJson(tokenAdmin)), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(TestJson.comeMappa(resp.getBody()).get("success")).isEqualTo(true);
    }

    @Test
    void nonAdminCannotRegisterUsers() {
        Map<String, Object> body = Map.of(
                "username", "x", "email", "x@validation.test", "password", "password1234", "name", "X");

        ResponseEntity<String> resp = rest.exchange(
                "/api/admin/users", HttpMethod.POST, new HttpEntity<>(body, bearerJson(tokenUser)), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void emailVuotaRestituisceIlCodiceStoricoMissingEmail() throws Exception {
        Map<String, Object> body = Map.of("email", "", "password", "irrilevante");

        ResponseEntity<String> resp = rest.exchange(
                "/api/auth/login", HttpMethod.POST,
                new HttpEntity<>(body, jsonHeaders()), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        // Deve restituire ESATTAMENTE il codice legacy, non il generico VALIDATION_ERROR:
        // conferma che @Valid non e' stato aggiunto al login (altrimenti l'ordine con
        // il rate limiter cambierebbe).
        assertThat(TestJson.comeMappa(resp.getBody()).get("error")).isEqualTo("MISSING_EMAIL");
    }

    @Test
    void passwordVuotaRestituisceIlCodiceStoricoMissingPassword() throws Exception {
        Map<String, Object> body = Map.of("email", "admin@validation.test", "password", "");

        ResponseEntity<String> resp = rest.exchange(
                "/api/auth/login", HttpMethod.POST,
                new HttpEntity<>(body, jsonHeaders()), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(TestJson.comeMappa(resp.getBody()).get("error")).isEqualTo("MISSING_PASSWORD");
    }

    @Test
    @SuppressWarnings("unchecked")
    void laFormaDellaRispostaDiLoginEBloccata() throws Exception {
        // Test di contratto: il frontend legge queste chiavi esatte. Serve a impedire che
        // un refactor le rinomini o ne aggiunga in silenzio. Stava nel modulo applicativo,
        // che pero' non serve piu' /api/auth/login.
        ResponseEntity<String> resp = rest.postForEntity(
                "/api/auth/login",
                Map.of("email", "user@validation.test", "password", "user-password"),
                String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = TestJson.comeMappa(resp.getBody());
        assertThat(body.keySet()).containsExactlyInAnyOrder(
                "success", "message", "token", "data", "timestamp", "sessionId");

        Map<String, Object> data = (Map<String, Object>) body.get("data");
        assertThat(data.keySet()).containsExactlyInAnyOrder(
                "token", "user", "loginTime", "tokenType");

        Map<String, Object> user = (Map<String, Object>) data.get("user");
        assertThat(user.keySet()).containsExactlyInAnyOrder(
                "id", "username", "name", "email", "role");
    }

    @Test
    void aggiornareUnUtenteConPasswordVuotaNonNeCambiaLaPassword() {
        // Una stringa vuota significa "non toccare la password", non "impostala a vuoto".
        // Se questa distinzione si rompesse, un admin che rinomina un utente lo lascerebbe
        // senza credenziali funzionanti, e il sintomo comparirebbe solo al login successivo.
        Long id = userRepository.findByEmail("user@validation.test").getId();

        Map<String, Object> body = Map.of(
                "username", "user-validation",
                "email", "user@validation.test",
                "password", "",
                "name", "User Validation Rinominato",
                "role", "user");

        ResponseEntity<String> resp = rest.exchange(
                "/api/admin/users/" + id, HttpMethod.PUT,
                new HttpEntity<>(body, bearerJson(tokenAdmin)), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);

        // la prova: la vecchia password deve ancora funzionare
        assertThat(login("user@validation.test", "user-password")).isNotBlank();
    }
}
