package com.prenotazioni;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prenotazioni.model.Notifica;
import com.prenotazioni.model.Utente;
import com.prenotazioni.model.Ruolo;
import com.prenotazioni.repository.IUtenteRepository;
import com.prenotazioni.repository.NotificaRepository;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Copre gli endpoint notifiche finora senza test: /non-lette, /mark-all-read e
 * DELETE /read.
 *
 * Ogni test verifica anche l'isolamento fra utenti: le operazioni "di massa"
 * (segna tutte come lette, elimina le lette) non devono toccare le notifiche altrui.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class NotificaEndpointsTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private IUtenteRepository utenteRepository;

    @Autowired
    private NotificaRepository notificaRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Utente owner;
    private Utente other;
    private String tokenOwner;
    private String tokenOther;
    private Long notificaLettaId;

    @BeforeEach
    void setUp() {
        notificaRepository.deleteAll();
        utenteRepository.deleteAll();

        owner = salvaUtente("notif-owner@test.it", "notif-owner", "owner-password");
        other = salvaUtente("notif-other@test.it", "notif-other", "other-password");

        // owner: 2 non lette + 1 gia' letta
        notificaRepository.save(new Notifica(owner, "Prima", "Messaggio 1", "INFO"));
        notificaRepository.save(new Notifica(owner, "Seconda", "Messaggio 2", "INFO"));
        Notifica letta = new Notifica(owner, "Terza", "Gia' letta", "INFO");
        letta.setLetta(true);
        notificaLettaId = notificaRepository.save(letta).getId();

        // other: 1 non letta, che non deve mai essere toccata dalle operazioni di owner
        notificaRepository.save(new Notifica(other, "Altrui", "Non toccare", "INFO"));

        tokenOwner = login("notif-owner@test.it", "owner-password");
        tokenOther = login("notif-other@test.it", "other-password");
    }

    private Utente salvaUtente(String email, String username, String rawPassword) {
        Utente u = new Utente();
        u.setEmail(email);
        u.setUsername(username);
        u.setPassword(passwordEncoder.encode(rawPassword));
        u.setNome(username);
        u.setRuolo(Ruolo.USER);
        u.setDataRegistrazione(LocalDateTime.now());
        return utenteRepository.save(u);
    }

    @SuppressWarnings("unchecked")
    private String login(String email, String password) {
        ResponseEntity<Map> resp = rest.postForEntity(
                "/api/auth/login", Map.of("email", email, "password", password), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) resp.getBody().get("token");
    }

    private ResponseEntity<String> exchange(String url, HttpMethod method, String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return rest.exchange(url, method, new HttpEntity<>(h), String.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(String json) throws Exception {
        return objectMapper.readValue(json, Map.class);
    }

    private long contaNonLette(Utente u) {
        return notificaRepository.findAll().stream()
                .filter(n -> n.getUtente().getId().equals(u.getId()))
                .filter(n -> !Boolean.TRUE.equals(n.getLetta()))
                .count();
    }

    @Test
    void nonLetteReturnsOnlyUnreadOfCaller() {
        ResponseEntity<Notifica[]> resp = rest.exchange(
                "/api/notifiche/non-lette", HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(tokenOwner)), Notifica[].class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        // 2 non lette di owner: quella gia' letta e quella di other sono escluse
        assertThat(resp.getBody()).hasSize(2);
    }

    @Test
    void markAllReadClearsOnlyCallersNotifications() throws Exception {
        ResponseEntity<String> resp = exchange("/api/notifiche/mark-all-read", HttpMethod.PUT, tokenOwner);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(asMap(resp.getBody()).get("message"))
                .isEqualTo("Tutte le notifiche sono state segnate come lette");

        assertThat(contaNonLette(owner)).isZero();
        // la notifica dell'altro utente resta non letta
        assertThat(contaNonLette(other)).isEqualTo(1);
    }

    @Test
    void countNonLetteIsZeroAfterMarkAllRead() throws Exception {
        exchange("/api/notifiche/mark-all-read", HttpMethod.PUT, tokenOwner);

        ResponseEntity<String> resp = exchange("/api/notifiche/count-non-lette", HttpMethod.GET, tokenOwner);
        assertThat(asMap(resp.getBody()).get("count")).isEqualTo(0);
    }

    @Test
    void deleteReadRemovesOnlyReadNotificationsOfCaller() throws Exception {
        ResponseEntity<String> resp = exchange("/api/notifiche/read", HttpMethod.DELETE, tokenOwner);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(asMap(resp.getBody()).get("message")).isEqualTo("Notifiche lette eliminate con successo");

        // sparisce solo quella gia' letta; le 2 non lette restano
        assertThat(notificaRepository.existsById(notificaLettaId)).isFalse();
        assertThat(contaNonLette(owner)).isEqualTo(2);
    }

    @Test
    void deleteReadAfterMarkAllReadEmptiesOwnInbox() {
        exchange("/api/notifiche/mark-all-read", HttpMethod.PUT, tokenOwner);
        exchange("/api/notifiche/read", HttpMethod.DELETE, tokenOwner);

        long rimasteDiOwner = notificaRepository.findAll().stream()
                .filter(n -> n.getUtente().getId().equals(owner.getId()))
                .count();
        assertThat(rimasteDiOwner).isZero();

        // l'inbox dell'altro utente e' intatta
        long rimasteDiOther = notificaRepository.findAll().stream()
                .filter(n -> n.getUtente().getId().equals(other.getId()))
                .count();
        assertThat(rimasteDiOther).isEqualTo(1);
    }

    @Test
    void otherUserCannotMarkOwnersNotificationAsRead() {
        Long idDiOwner = notificaRepository.findAll().stream()
                .filter(n -> n.getUtente().getId().equals(owner.getId()))
                .findFirst().orElseThrow().getId();

        ResponseEntity<String> resp = exchange(
                "/api/notifiche/" + idDiOwner + "/mark-read", HttpMethod.PUT, tokenOther);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void notificaEndpointsRequireAuthentication() {
        for (String url : new String[]{"/api/notifiche/non-lette", "/api/notifiche/count-non-lette"}) {
            ResponseEntity<String> resp = rest.exchange(url, HttpMethod.GET, HttpEntity.EMPTY, String.class);
            assertThat(resp.getStatusCode())
                    .as("endpoint %s senza token", url)
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    private HttpHeaders bearerHeaders(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return h;
    }
}
