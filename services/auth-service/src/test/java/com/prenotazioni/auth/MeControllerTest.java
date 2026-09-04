package com.prenotazioni.auth;

import com.prenotazioni.testsupport.TestJson;
import com.prenotazioni.auth.model.Utente;
import com.prenotazioni.model.Ruolo;
import com.prenotazioni.auth.repository.IUtenteRepository;
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
 * Il profilo dell'utente autenticato.
 *
 * Conteneva anche cinque test sulle notifiche, spostati in NotificaOwnershipTest dentro
 * notifica-service: stavano insieme solo perche' condividevano la fixture di utenti e il
 * login, non perche' verificassero la stessa cosa.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MeControllerTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private IUtenteRepository utenteRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Utente owner;
    private Utente other;
    private String tokenOwner;
    private String tokenOther;

    @BeforeEach
    void setUp() {
        utenteRepository.deleteAll();

        owner = new Utente();
        owner.setEmail("me-owner@test.it");
        owner.setUsername("me-owner");
        owner.setPassword(passwordEncoder.encode("owner-password"));
        owner.setNome("Me Owner");
        owner.setRuolo(Ruolo.USER);
        owner.setDataRegistrazione(LocalDateTime.now());
        utenteRepository.save(owner);

        other = new Utente();
        other.setEmail("me-other@test.it");
        other.setUsername("me-other");
        other.setPassword(passwordEncoder.encode("other-password"));
        other.setNome("Me Other");
        other.setRuolo(Ruolo.USER);
        other.setDataRegistrazione(LocalDateTime.now());
        utenteRepository.save(other);

        tokenOwner = login("me-owner@test.it", "owner-password");
        tokenOther = login("me-other@test.it", "other-password");
    }

    @SuppressWarnings("unchecked")
    private String login(String email, String password) {
        Map<String, String> body = Map.of("email", email, "password", password);
        ResponseEntity<Map> resp = rest.postForEntity("/api/auth/login", body, Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) resp.getBody().get("token");
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }


    // ==================== MeController ====================

    @Test
    void restituisceIlProprioProfiloSenzaLaPassword() throws Exception {
        ResponseEntity<String> resp = rest.exchange(
                "/api/me", HttpMethod.GET, new HttpEntity<>(bearer(tokenOwner)), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = TestJson.comeMappa(resp.getBody());
        assertThat(body.get("success")).isEqualTo(true);
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        assertThat(data.get("email")).isEqualTo("me-owner@test.it");
        assertThat(data.get("username")).isEqualTo("me-owner");
        assertThat(data).doesNotContainKey("password");
        assertThat(resp.getBody()).doesNotContain("owner-password");
    }

    @Test
    void senzaTokenRisponde401() {
        ResponseEntity<String> resp = rest.exchange(
                "/api/me", HttpMethod.GET, HttpEntity.EMPTY, String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
