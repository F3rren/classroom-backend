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
 * Validation of registration and login.
 *
 * These tests used to live in ValidationAndAdminTest, in the application module, alongside
 * the validation of rooms and bookings. With the split they followed the endpoints they
 * check: /api/admin/users and /api/auth/login belong to this service, and answer 404 from
 * the application module.
 *
 * The two tests on the login's "legacy" codes are the most important in the file: the checks
 * on empty email and password are manual and run AFTER the rate limiter, so they return
 * codes of their own instead of Bean Validation's VALIDATION_ERROR. See the javadoc of
 * LoginRequest for why that order must not be changed.
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
        saveUser("admin@validation.test", "admin-validation", "admin-password", Role.ADMIN);
        saveUser("user@validation.test", "user-validation", "user-password", Role.USER);

        tokenAdmin = login("admin@validation.test", "admin-password");
        tokenUser = login("user@validation.test", "user-password");
    }

    private void saveUser(String email, String username, String rawPassword, Role role) {
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
    void anInvalidEmailIsRejectedByValidation() throws Exception {
        Map<String, Object> body = Map.of(
                "username", "nuovoutente",
                "email", "non-e-una-email",
                "password", "password1234",
                "name", "Nuovo Utente",
                "role", "user");

        ResponseEntity<String> resp = rest.exchange(
                "/api/admin/users", HttpMethod.POST, new HttpEntity<>(body, bearerJson(tokenAdmin)), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Map<String, Object> responseBody = TestJson.asMap(resp.getBody());
        assertThat(responseBody.get("error")).isEqualTo("VALIDATION_ERROR");
        assertThat(responseBody.get("success")).isEqualTo(false);
    }

    @Test
    void aTooShortPasswordIsRejected() throws Exception {
        Map<String, Object> body = Map.of(
                "username", "nuovoutente2",
                "email", "nuovoutente2@validation.test",
                "password", "short",
                "name", "Nuovo Utente 2",
                "role", "user");

        ResponseEntity<String> resp = rest.exchange(
                "/api/admin/users", HttpMethod.POST, new HttpEntity<>(body, bearerJson(tokenAdmin)), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(TestJson.asMap(resp.getBody()).get("error")).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void adminRegisterWithAnInvalidRoleIsRejected() throws Exception {
        Map<String, Object> body = Map.of(
                "username", "nuovoutente3",
                "email", "nuovoutente3@validation.test",
                "password", "password1234",
                "name", "Nuovo Utente 3",
                "role", "superadmin");

        ResponseEntity<String> resp = rest.exchange(
                "/api/admin/users", HttpMethod.POST, new HttpEntity<>(body, bearerJson(tokenAdmin)), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(TestJson.asMap(resp.getBody()).get("error")).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void validDataCreatesTheUser() throws Exception {
        Map<String, Object> body = Map.of(
                "username", "nuovoutente4",
                "email", "nuovoutente4@validation.test",
                "password", "password1234",
                "name", "Nuovo Utente 4",
                "role", "user");

        ResponseEntity<String> resp = rest.exchange(
                "/api/admin/users", HttpMethod.POST, new HttpEntity<>(body, bearerJson(tokenAdmin)), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(TestJson.asMap(resp.getBody()).get("success")).isEqualTo(true);
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
    void anEmptyEmailReturnsTheHistoricMissingEmailCode() throws Exception {
        Map<String, Object> body = Map.of("email", "", "password", "irrilevante");

        ResponseEntity<String> resp = rest.exchange(
                "/api/auth/login", HttpMethod.POST,
                new HttpEntity<>(body, jsonHeaders()), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        // It has to return EXACTLY the legacy code, not the generic VALIDATION_ERROR: that
        // confirms @Valid was not added to the login (which would change the order with
        // respect to the rate limiter).
        assertThat(TestJson.asMap(resp.getBody()).get("error")).isEqualTo("MISSING_EMAIL");
    }

    @Test
    void anEmptyPasswordReturnsTheHistoricMissingPasswordCode() throws Exception {
        Map<String, Object> body = Map.of("email", "admin@validation.test", "password", "");

        ResponseEntity<String> resp = rest.exchange(
                "/api/auth/login", HttpMethod.POST,
                new HttpEntity<>(body, jsonHeaders()), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(TestJson.asMap(resp.getBody()).get("error")).isEqualTo("MISSING_PASSWORD");
    }

    @Test
    @SuppressWarnings("unchecked")
    void theShapeOfTheLoginResponseIsLocked() throws Exception {
        // A contract test: the frontend reads these exact keys. It is here to stop a
        // refactor renaming them or adding some in silence. It used to live in the
        // application module, which no longer serves /api/auth/login.
        ResponseEntity<String> resp = rest.postForEntity(
                "/api/auth/login",
                Map.of("email", "user@validation.test", "password", "user-password"),
                String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = TestJson.asMap(resp.getBody());
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
    void updatingAUserWithAnEmptyPasswordLeavesThePasswordUnchanged() {
        // An empty string means "leave the password alone", not "set it to empty". If that
        // distinction broke, an admin renaming a user would leave them with no working
        // credentials, and the symptom would only surface at their next login.
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

        // the proof: the old password still has to work
        assertThat(login("user@validation.test", "user-password")).isNotBlank();
    }
}
