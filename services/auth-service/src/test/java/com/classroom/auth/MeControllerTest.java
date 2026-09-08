package com.classroom.auth;

import com.classroom.testsupport.TestJson;
import com.classroom.auth.model.User;
import com.classroom.model.Role;
import com.classroom.auth.repository.UserRepository;
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
import org.springframework.lang.NonNull;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The profile of the authenticated user.
 *
 * It also held five tests on notifications, moved to NotificationOwnershipTest inside
 * notification-service: they sat together only because they shared the user fixture and the
 * login, not because they checked the same thing.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MeControllerTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User owner;
    private User other;
    private String tokenOwner;
    private String tokenOther;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        owner = new User();
        owner.setEmail("me-owner@test.it");
        owner.setUsername("me-owner");
        owner.setPassword(passwordEncoder.encode("owner-password"));
        owner.setName("Me Owner");
        owner.setRole(Role.USER);
        owner.setRegisteredAt(LocalDateTime.now());
        userRepository.save(owner);

        other = new User();
        other.setEmail("me-other@test.it");
        other.setUsername("me-other");
        other.setPassword(passwordEncoder.encode("other-password"));
        other.setName("Me Other");
        other.setRole(Role.USER);
        other.setRegisteredAt(LocalDateTime.now());
        userRepository.save(other);

        tokenOwner = login("me-owner@test.it", "owner-password");
        tokenOther = login("me-other@test.it", "other-password");
    }

    @SuppressWarnings("unchecked")
    private String login(String email, String password) {
        Map<String, String> body = Map.of("email", email, "password", password);
        ResponseEntity<Map> resp = rest.postForEntity("/api/auth/login", body, Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) Objects.requireNonNull(resp.getBody()).get("token");
    }

    @NonNull
    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(Objects.requireNonNull(token));
        return headers;
    }


    // ==================== MeController ====================

    @Test
    @SuppressWarnings("unchecked")
    void returnsTheOwnProfileWithoutThePassword() throws Exception {
        ResponseEntity<String> resp = rest.exchange(
                "/api/me", HttpMethod.GET, new HttpEntity<>(bearer(tokenOwner)), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = TestJson.asMap(resp.getBody());
        assertThat(body.get("success")).isEqualTo(true);
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        assertThat(data.get("email")).isEqualTo("me-owner@test.it");
        assertThat(data.get("username")).isEqualTo("me-owner");
        assertThat(data).doesNotContainKey("password");
        assertThat(resp.getBody()).doesNotContain("owner-password");
    }

    @Test
    void answers401WithoutAToken() {
        ResponseEntity<String> resp = rest.exchange(
                "/api/me", HttpMethod.GET, HttpEntity.EMPTY, String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
