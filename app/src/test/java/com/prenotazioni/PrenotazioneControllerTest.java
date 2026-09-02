package com.prenotazioni;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prenotazioni.model.Aula;
import com.prenotazioni.model.StatoAula;
import com.prenotazioni.model.Prenotazione;
import com.prenotazioni.model.StatoPrenotazione;
import com.prenotazioni.model.Utente;
import com.prenotazioni.model.Ruolo;
import com.prenotazioni.model.ProprietarioPrenotazione;
import com.prenotazioni.repository.IAulaRepository;
import com.prenotazioni.repository.IPrenotazioneRepository;
import com.prenotazioni.repository.IUtenteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
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
 * Regression suite per il fix IDOR/leak-password su /api/prenotazioni.
 * Owner (A) crea una prenotazione; Other (B), senza alcun rapporto con essa,
 * non deve poterla leggere ne' vederne la password in chiaro.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
// Isola il contesto (e quindi lo schema H2) da questa classe: senza, dati lasciati da
// altre classi di test @SpringBootTest nello stesso DB in-memory condiviso possono violare
// vincoli FK qui (es. un Utente referenziato da una Notifica creata da un'altra classe).
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PrenotazioneControllerTest {

    @LocalServerPort
    private int port;

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

    private Long prenotazioneIdDiOwner;
    private Long aulaId;
    private String tokenOwner;
    private String tokenOther;

    @BeforeEach
    void setUp() {
        prenotazioneRepository.deleteAll();
        aulaRepository.deleteAll();
        utenteRepository.deleteAll();

        Utente owner = nuovoUtente("owner@test.it", "owner", "password-owner", "Owner Test");
        utenteRepository.save(owner);

        Utente other = nuovoUtente("other@test.it", "other", "password-other", "Other Test");
        utenteRepository.save(other);

        Aula aula = new Aula();
        aula.setNome("Aula IT Test");
        aula.setPiano(1);
        aula.setCapienza(20);
        aula.setVirtual(false);
        aula.setStato(StatoAula.LIBERA);
        aulaRepository.save(aula);
        aulaId = aula.getId();

        Prenotazione prenotazione = new Prenotazione();
        prenotazione.setAula(aula);
        prenotazione.setUtente(istantaneaDi(owner));
        prenotazione.setInizio(LocalDateTime.now().plusDays(1));
        prenotazione.setFine(LocalDateTime.now().plusDays(1).plusHours(2));
        prenotazione.setStato(StatoPrenotazione.PRENOTATA);
        prenotazione.setDescrizione("Riunione privata di owner");
        prenotazione.setDataCreazione(LocalDateTime.now());
        prenotazioneRepository.save(prenotazione);
        prenotazioneIdDiOwner = prenotazione.getId();

        tokenOwner = login("owner@test.it", "password-owner");
        tokenOther = login("other@test.it", "password-other");
    }

    private Utente nuovoUtente(String email, String username, String rawPassword, String nome) {
        Utente u = new Utente();
        u.setEmail(email);
        u.setUsername(username);
        u.setPassword(passwordEncoder.encode(rawPassword));
        u.setNome(nome);
        u.setRuolo(Ruolo.USER);
        u.setDataRegistrazione(LocalDateTime.now());
        return u;
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

    @Test
    void otherUserCannotReadOwnersBooking() {
        ResponseEntity<String> resp = rest.exchange(
                "/api/prenotazioni/" + prenotazioneIdDiOwner,
                HttpMethod.GET,
                new HttpEntity<>(bearer(tokenOther)),
                String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resp.getBody()).doesNotContain("Riunione privata di owner");
    }

    @Test
    void otherUserCannotReadOwnersBookingDetails() {
        ResponseEntity<String> resp = rest.exchange(
                "/api/prenotazioni/" + prenotazioneIdDiOwner + "/details",
                HttpMethod.GET,
                new HttpEntity<>(bearer(tokenOther)),
                String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void ownerCanStillReadTheirOwnBooking() {
        ResponseEntity<String> resp = rest.exchange(
                "/api/prenotazioni/" + prenotazioneIdDiOwner,
                HttpMethod.GET,
                new HttpEntity<>(bearer(tokenOwner)),
                String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).contains("Riunione privata di owner");
    }

    @Test
    void passwordIsNeverSerializedInAnyPrenotazioneResponse() {
        ResponseEntity<String> ownerView = rest.exchange(
                "/api/prenotazioni/" + prenotazioneIdDiOwner,
                HttpMethod.GET,
                new HttpEntity<>(bearer(tokenOwner)),
                String.class);
        assertThat(ownerView.getBody()).doesNotContain("password-owner");
        assertThat(ownerView.getBody()).doesNotContain("\"password\"");

        ResponseEntity<String> listView = rest.exchange(
                "/api/prenotazioni",
                HttpMethod.GET,
                new HttpEntity<>(bearer(tokenOther)),
                String.class);
        assertThat(listView.getBody()).doesNotContain("password-owner");
        assertThat(listView.getBody()).doesNotContain("\"password\"");
    }

    @Test
    void listEndpointHidesOwnerPiiFromOtherAuthenticatedUsers() {
        ResponseEntity<String> resp = rest.exchange(
                "/api/prenotazioni",
                HttpMethod.GET,
                new HttpEntity<>(bearer(tokenOther)),
                String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).doesNotContain("owner@test.it");
    }

    // ==================== SHAPE-LOCK: blocca derive accidentali di forma durante il refactor Swagger ====================

    private final ObjectMapper objectMapper = new ObjectMapper();

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(String json) throws Exception {
        return objectMapper.readValue(json, Map.class);
    }

    @Test
    void loginSuccessResponseShapeIsLocked() throws Exception {
        ResponseEntity<String> resp = rest.postForEntity(
                "/api/auth/login",
                Map.of("email", "owner@test.it", "password", "password-owner"),
                String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = asMap(resp.getBody());
        assertThat(body.keySet()).containsExactlyInAnyOrder(
                "success", "message", "token", "data", "timestamp", "sessionId");

        Map<String, Object> data = (Map<String, Object>) body.get("data");
        assertThat(data.keySet()).containsExactlyInAnyOrder(
                "token", "user", "loginTime", "tokenType");

        Map<String, Object> user = (Map<String, Object>) data.get("user");
        assertThat(user.keySet()).containsExactlyInAnyOrder(
                "id", "username", "nome", "email", "ruolo");
    }

    @Test
    void prenotaSuccessResponseShapeIsLocked() throws Exception {
        HttpHeaders headers = bearer(tokenOwner);
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        Map<String, Object> body = Map.of(
                "aulaId", aulaId,
                "inizio", LocalDateTime.now().plusDays(2).toString(),
                "fine", LocalDateTime.now().plusDays(2).plusHours(1).toString());

        ResponseEntity<String> resp = rest.exchange(
                "/api/prenotazioni/prenota",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> responseBody = asMap(resp.getBody());
        assertThat(responseBody.keySet()).containsExactlyInAnyOrder(
                "success", "message", "data", "timestamp", "sessionId");

        Map<String, Object> data = (Map<String, Object>) responseBody.get("data");
        assertThat(data.keySet()).containsExactlyInAnyOrder("prenotazione", "aulaId", "periodo");
    }

    @Test
    void missingFieldErrorResponseShapeIsLocked() throws Exception {
        HttpHeaders headers = bearer(tokenOwner);
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        // aulaId mancante
        Map<String, Object> body = Map.of(
                "inizio", LocalDateTime.now().plusDays(2).toString(),
                "fine", LocalDateTime.now().plusDays(2).plusHours(1).toString());

        ResponseEntity<String> resp = rest.exchange(
                "/api/prenotazioni/prenota",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Map<String, Object> responseBody = asMap(resp.getBody());
        assertThat(responseBody.keySet()).containsExactlyInAnyOrder(
                "success", "error", "message", "userMessage", "timestamp", "sessionId");
        assertThat(responseBody.get("success")).isEqualTo(false);
    }

    // ==================== Annullamento: regressione doppio annullamento ====================

    @Test
    void ownerCanCancelTheirOwnBooking() {
        ResponseEntity<String> resp = rest.exchange(
                "/api/prenotazioni/" + prenotazioneIdDiOwner, HttpMethod.DELETE,
                new HttpEntity<>(bearer(tokenOwner)), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(prenotazioneRepository.findById(prenotazioneIdDiOwner).orElseThrow().getStato())
                .isEqualTo(StatoPrenotazione.ANNULLATA);
    }

    @Test
    void cancellingTwiceIsRejectedInsteadOfSilentlySucceeding() throws Exception {
        rest.exchange("/api/prenotazioni/" + prenotazioneIdDiOwner, HttpMethod.DELETE,
                new HttpEntity<>(bearer(tokenOwner)), String.class);

        ResponseEntity<String> secondo = rest.exchange(
                "/api/prenotazioni/" + prenotazioneIdDiOwner, HttpMethod.DELETE,
                new HttpEntity<>(bearer(tokenOwner)), String.class);

        assertThat(secondo.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(asMap(secondo.getBody()).get("error")).isEqualTo("INVALID_STATE");
    }

    @Test
    void strangerCannotCancelSomeoneElsesBooking() throws Exception {
        ResponseEntity<String> resp = rest.exchange(
                "/api/prenotazioni/" + prenotazioneIdDiOwner, HttpMethod.DELETE,
                new HttpEntity<>(bearer(tokenOther)), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(asMap(resp.getBody()).get("error")).isEqualTo("ACCESS_DENIED");
        // la prenotazione resta intatta
        assertThat(prenotazioneRepository.findById(prenotazioneIdDiOwner).orElseThrow().getStato())
                .isEqualTo(StatoPrenotazione.PRENOTATA);
    }

    @Test
    void cancellingAMissingBookingReturns404() {
        ResponseEntity<String> resp = rest.exchange(
                "/api/prenotazioni/999999", HttpMethod.DELETE,
                new HttpEntity<>(bearer(tokenOwner)), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    /** L'istantanea del proprietario che la prenotazione conserva al posto della relazione JPA. */
    private static ProprietarioPrenotazione istantaneaDi(Utente utente) {
        return new ProprietarioPrenotazione(utente.getId(), utente.getUsername(), utente.getNome());
    }
}
