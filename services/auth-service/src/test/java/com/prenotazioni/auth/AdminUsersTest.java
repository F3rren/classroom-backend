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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Amministrazione degli utenti dopo la separazione.
 *
 * Questi casi stavano in AdminManagementTest, nel modulo applicativo, che li verificava
 * insieme ad aule e prenotazioni. Sono seguiti gli endpoint.
 *
 * Il test sulla cancellazione e' il piu' importante: prima era una transazione unica con
 * chiavi esterne, ora e' una sequenza di chiamate di rete che puo' fallire a meta'. Qui i
 * servizi a valle non esistono, quindi ogni cancellazione fallisce - ed e' proprio la
 * condizione che serve verificare, perche' dimostra che l'utente NON viene rimosso quando
 * i suoi dati altrove non lo sono. Se fosse il contrario, resterebbero righe orfane di cui
 * nessuno saprebbe piu' il proprietario.
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

    @SuppressWarnings("unchecked")
    private String login(String email) {
        ResponseEntity<Map> resp = rest.postForEntity(
                "/api/auth/login", Map.of("email", email, "password", "password-di-prova"), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) resp.getBody().get("token");
    }

    private HttpHeaders headers() {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(tokenAdmin);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private ResponseEntity<String> chiama(String url, HttpMethod metodo, Object body) {
        return rest.exchange(url, metodo, new HttpEntity<>(body, headers()), String.class);
    }


    @Test
    @SuppressWarnings("unchecked")
    void listaUtentiNonEsponeLePassword() throws Exception {
        ResponseEntity<String> resp = chiama("/api/admin/users", HttpMethod.GET, null);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).doesNotContain("password");

        Map<String, Object> data = (Map<String, Object>) TestJson.bodyOf(resp).get("data");
        assertThat((List<Object>) data.get("users")).hasSize(2);
    }

    @Test
    void registrazioneConEmailGiaUsataVieneRifiutata() throws Exception {
        Map<String, String> body = Map.of("username", "nuovo", "name", "Nuovo",
                "email", "normale@test.it", "password", "password-lunga", "role", "user");

        ResponseEntity<String> resp = chiama("/api/admin/users", HttpMethod.POST, body);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(TestJson.bodyOf(resp).get("success")).isEqualTo(false);
    }

    @Test
    void registrazioneConUsernameGiaUsatoVieneRifiutata() throws Exception {
        Map<String, String> body = Map.of("username", "normale", "name", "Nuovo",
                "email", "un-altra@test.it", "password", "password-lunga", "role", "user");

        ResponseEntity<String> resp = chiama("/api/admin/users", HttpMethod.POST, body);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void aggiornamentoDiUnUtenteInesistenteRisponde404() {
        Map<String, String> body = Map.of("username", "x", "name", "X", "email", "x@test.it");

        ResponseEntity<String> resp = chiama("/api/admin/users/999999", HttpMethod.PUT, body);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void cancellazioneDiUnUtenteInesistenteRisponde404() {
        ResponseEntity<String> resp = chiama("/api/admin/users/999999", HttpMethod.DELETE, null);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void seIServiziAValleNonRispondonoLUtenteNonVieneCancellato() {
        // In questo test non esistono ne' prenotazione-service ne' notifica-service: le chiamate
        // falliscono, e l'utente deve restare. E' la garanzia che sostituisce la chiave
        // esterna persa con la separazione.
        ResponseEntity<String> resp = chiama("/api/admin/users/" + regularUserId, HttpMethod.DELETE, null);

        assertThat(resp.getStatusCode()).isNotEqualTo(HttpStatus.OK);
        assertThat(userRepository.findById(regularUserId))
                .as("l'utente non deve sparire se le sue prenotazioni non sono state cancellate")
                .isPresent();
    }
}
