package com.classroom.auth;

import com.classroom.testsupport.TestJson;
import com.classroom.auth.model.User;
import com.classroom.auth.repository.UserRepository;
import com.classroom.model.Role;
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
import org.springframework.lang.NonNull;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * User administration after the split.
 *
 * These cases used to live in AdminManagementTest, in the application module, which checked
 * them alongside rooms and bookings. They followed their endpoints.
 *
 * The deletion test is the most important one: deleting a user used to be a sequence of
 * network calls to the other two services, and the response depended on both succeeding.
 * Now it publishes a UserDeletedEvent and returns without waiting for anybody. There is no
 * broker in this test context, so the publish itself fails - and that is exactly the
 * condition worth checking, because it proves the deletion no longer depends on it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AdminUsersTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private String tokenAdmin;
    private Long regularUserId;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        save("admin-utenti@test.it", "admin-utenti", Role.ADMIN);
        regularUserId = save("normale@test.it", "normale", Role.USER);
        tokenAdmin = login("admin-utenti@test.it");
    }

    private Long save(String email, String username, Role role) {
        User u = new User();
        u.setEmail(email);
        u.setUsername(username);
        u.setPassword(passwordEncoder.encode("password-di-prova"));
        u.setName(username);
        u.setRole(role);
        u.setRegisteredAt(LocalDateTime.now());
        return userRepository.save(u).getId();
    }

    private String login(String email) {
        ResponseEntity<Map> resp = rest.postForEntity(
                "/api/auth/login", Map.of("email", email, "password", "password-di-prova"), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) Objects.requireNonNull(resp.getBody()).get("token");
    }

    @NonNull
    private HttpHeaders headers() {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(Objects.requireNonNull(tokenAdmin));
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private ResponseEntity<String> call(String url, HttpMethod method, Object body) {
        return rest.exchange(url, method, new HttpEntity<>(body, headers()), String.class);
    }


    @Test
    @SuppressWarnings("unchecked")
    void theUserListNeverExposesPasswords() throws Exception {
        ResponseEntity<String> resp = call("/api/admin/users", HttpMethod.GET, null);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).doesNotContain("password");

        Map<String, Object> data = (Map<String, Object>) TestJson.bodyOf(resp).get("data");
        assertThat((List<Object>) data.get("users")).hasSize(2);
    }

    @Test
    void registeringWithAnAlreadyUsedEmailIsRejected() throws Exception {
        Map<String, String> body = Map.of("username", "nuovo", "name", "Nuovo",
                "email", "normale@test.it", "password", "password-lunga", "role", "user");

        ResponseEntity<String> resp = call("/api/admin/users", HttpMethod.POST, body);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(TestJson.bodyOf(resp).get("success")).isEqualTo(false);
    }

    @Test
    void registeringWithAnAlreadyUsedUsernameIsRejected() throws Exception {
        Map<String, String> body = Map.of("username", "normale", "name", "Nuovo",
                "email", "un-altra@test.it", "password", "password-lunga", "role", "user");

        ResponseEntity<String> resp = call("/api/admin/users", HttpMethod.POST, body);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void updatingAMissingUserAnswers404() {
        Map<String, String> body = Map.of("username", "x", "name", "X", "email", "x@test.it");

        ResponseEntity<String> resp = call("/api/admin/users/999999", HttpMethod.PUT, body);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deletingAMissingUserAnswers404() {
        ResponseEntity<String> resp = call("/api/admin/users/999999", HttpMethod.DELETE, null);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deletingAUserSucceedsEvenWhenTheBrokerIsUnreachable() {
        // No RabbitMQ container in this test: the publish underneath deleteUser fails,
        // caught and logged by EventPublisher. The deletion itself does not depend on it -
        // that is the point of moving it off the synchronous path.
        ResponseEntity<String> resp = call("/api/admin/users/" + regularUserId, HttpMethod.DELETE, null);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(userRepository.findById(Objects.requireNonNull(regularUserId))).isEmpty();
    }
}
