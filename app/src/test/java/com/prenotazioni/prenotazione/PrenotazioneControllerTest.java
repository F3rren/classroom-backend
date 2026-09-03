package com.prenotazioni.prenotazione;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prenotazioni.testsupport.TestJwt;
import com.prenotazioni.prenotazione.model.Aula;
import com.prenotazioni.prenotazione.model.StatoAula;
import com.prenotazioni.prenotazione.model.Prenotazione;
import com.prenotazioni.prenotazione.model.StatoPrenotazione;
import com.prenotazioni.model.Ruolo;
import com.prenotazioni.prenotazione.model.ProprietarioPrenotazione;
import com.prenotazioni.prenotazione.repository.IAulaRepository;
import com.prenotazioni.prenotazione.repository.IPrenotazioneRepository;
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
    private IAulaRepository aulaRepository;

    @Autowired
    private IPrenotazioneRepository prenotazioneRepository;

    private Long prenotazioneIdDiOwner;
    private Long aulaId;
    private String tokenOwner;
    private String tokenOther;

    @BeforeEach
    void setUp() {
        prenotazioneRepository.deleteAll();
        aulaRepository.deleteAll();

        ProprietarioPrenotazione owner = nuovoUtente(1L, "owner", "Owner Test");

        ProprietarioPrenotazione other = nuovoUtente(2L, "other", "Other Test");

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
        prenotazione.setUtente(owner);
        prenotazione.setInizio(LocalDateTime.now().plusDays(1));
        prenotazione.setFine(LocalDateTime.now().plusDays(1).plusHours(2));
        prenotazione.setStato(StatoPrenotazione.PRENOTATA);
        prenotazione.setDescrizione("Riunione privata di owner");
        prenotazione.setDataCreazione(LocalDateTime.now());
        prenotazioneRepository.save(prenotazione);
        prenotazioneIdDiOwner = prenotazione.getId();

        tokenOwner = TestJwt.perUtente(1L, "owner@test.it", "Owner Test");
        tokenOther = TestJwt.perUtente(2L, "other@test.it", "Other Test");
    }

    /** L'istantanea di un proprietario. Prima creava un utente vero: la tabella non e' piu' qui. */
    private ProprietarioPrenotazione nuovoUtente(Long id, String username, String nome) {
        return new ProprietarioPrenotazione(id, username, nome);
    }

    @SuppressWarnings("unchecked")
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

    // La forma della risposta di login e' verificata in auth-service, che ora possiede
    // /api/auth/login: da qui quell'endpoint risponde 404.

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

}
