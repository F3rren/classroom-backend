package com.prenotazioni;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prenotazioni.model.Aula;
import com.prenotazioni.model.StatoAula;
import com.prenotazioni.model.Prenotazione;
import com.prenotazioni.model.StatoPrenotazione;
import com.prenotazioni.model.Utente;
import com.prenotazioni.model.Ruolo;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Copre gli endpoint di sola lettura di /api/prenotazioni finora senza test:
 * /mie, /future, /all-details, /disponibilita, /{id}/details e /stato/{...}.
 *
 * Il valore qui non e' solo "risponde 200": ogni test blocca anche l'esatto set di
 * chiavi JSON, perche' alcuni di questi endpoint NON sono avvolti nell'envelope
 * ApiEnvelope (shape storica preservata per il frontend) e la differenza va difesa.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PrenotazioneQueryTest {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

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

    private String token;
    private Long aulaId;
    private Long prenotazioneId;
    private LocalDateTime inizio;
    private LocalDateTime fine;

    @BeforeEach
    void setUp() {
        prenotazioneRepository.deleteAll();
        aulaRepository.deleteAll();
        utenteRepository.deleteAll();

        Utente user = new Utente();
        user.setEmail("query-user@test.it");
        user.setUsername("query-user");
        user.setPassword(passwordEncoder.encode("query-password"));
        user.setNome("Query User");
        user.setRuolo(Ruolo.USER);
        user.setDataRegistrazione(LocalDateTime.now());
        utenteRepository.save(user);

        Aula aula = new Aula();
        aula.setNome("Aula Query");
        aula.setPiano(2);
        aula.setCapienza(30);
        aula.setVirtual(false);
        aula.setStato(StatoAula.LIBERA);
        aulaId = aulaRepository.save(aula).getId();

        inizio = LocalDateTime.now().plusDays(2).withNano(0);
        fine = inizio.plusHours(2);

        Prenotazione p = new Prenotazione();
        p.setAula(aula);
        p.setUtente(user);
        p.setInizio(inizio);
        p.setFine(fine);
        p.setStato(StatoPrenotazione.PRENOTATA);
        p.setDescrizione("Prenotazione per test di query");
        p.setDataCreazione(LocalDateTime.now());
        prenotazioneId = prenotazioneRepository.save(p).getId();

        token = login("query-user@test.it", "query-password");
    }

    @SuppressWarnings("unchecked")
    private String login(String email, String password) {
        ResponseEntity<Map> resp = rest.postForEntity(
                "/api/auth/login", Map.of("email", email, "password", password), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) resp.getBody().get("token");
    }

    private HttpHeaders bearer() {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return h;
    }

    private ResponseEntity<String> get(String url) {
        return rest.exchange(url, HttpMethod.GET, new HttpEntity<>(bearer()), String.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(String json) throws Exception {
        return objectMapper.readValue(json, Map.class);
    }

    @Test
    void miePrenotazioniReturnsOnlyOwnBookingsWithoutEnvelope() throws Exception {
        ResponseEntity<String> resp = get("/api/prenotazioni/mie");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = asMap(resp.getBody());
        // shape storica: solo "prenotazioni", nessun envelope e nessun totale
        assertThat(body.keySet()).containsExactly("prenotazioni");
        assertThat((java.util.List<?>) body.get("prenotazioni")).hasSize(1);
    }

    @Test
    void miePrenotazioniExcludesCancelledBookings() throws Exception {
        Prenotazione p = prenotazioneRepository.findById(prenotazioneId).orElseThrow();
        p.setStato(StatoPrenotazione.ANNULLATA);
        prenotazioneRepository.save(p);

        Map<String, Object> body = asMap(get("/api/prenotazioni/mie").getBody());
        assertThat((java.util.List<?>) body.get("prenotazioni")).isEmpty();
    }

    @Test
    void futurePrenotazioniIncludesTotal() throws Exception {
        ResponseEntity<String> resp = get("/api/prenotazioni/future");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = asMap(resp.getBody());
        assertThat(body.keySet()).containsExactlyInAnyOrder("prenotazioni", "totalPrenotazioni");
        assertThat(body.get("totalPrenotazioni")).isEqualTo(1);
    }

    @Test
    void futurePrenotazioniStripsOwnerPii() {
        // La lista e' visibile a qualunque utente autenticato: il proprietario va
        // ridotto a id/username/nome, mai email o ruolo (sanitizeOwnerForListing).
        ResponseEntity<String> resp = get("/api/prenotazioni/future");

        assertThat(resp.getBody()).doesNotContain("query-user@test.it");
        assertThat(resp.getBody()).doesNotContain("query-password");
    }

    @Test
    void allDetailsReturnsDettaglioListPayload() throws Exception {
        ResponseEntity<String> resp = get("/api/prenotazioni/all-details");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = asMap(resp.getBody());
        assertThat(body.keySet()).containsExactlyInAnyOrder("prenotazioni", "totalPrenotazioni");
        assertThat(body.get("totalPrenotazioni")).isEqualTo(1);
    }

    @Test
    void prenotazioneDetailsByIdReturnsDettagli() throws Exception {
        ResponseEntity<String> resp = get("/api/prenotazioni/" + prenotazioneId + "/details");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = asMap(resp.getBody());
        assertThat(body.keySet()).containsExactlyInAnyOrder(
                "prenotazione", "dettagliCompleti", "totalDettagli");
    }

    @Test
    void disponibilitaReportsFreeSlotAsLibera() throws Exception {
        String libero = inizio.plusDays(5).format(ISO);
        String liberoFine = inizio.plusDays(5).plusHours(1).format(ISO);

        ResponseEntity<String> resp = get(
                "/api/prenotazioni/disponibilita?aulaId=" + aulaId + "&inizio=" + libero + "&fine=" + liberoFine);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> data = castMap(asMap(resp.getBody()).get("data"));
        assertThat(data.keySet()).containsExactlyInAnyOrder("aulaId", "disponibile", "periodo", "status");
        assertThat(data.get("disponibile")).isEqualTo(true);
        assertThat(data.get("status")).isEqualTo("LIBERA");
    }

    @Test
    void disponibilitaReportsBookedSlotAsOccupata() throws Exception {
        ResponseEntity<String> resp = get(
                "/api/prenotazioni/disponibilita?aulaId=" + aulaId
                        + "&inizio=" + inizio.format(ISO) + "&fine=" + fine.format(ISO));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> data = castMap(asMap(resp.getBody()).get("data"));
        assertThat(data.get("disponibile")).isEqualTo(false);
        assertThat(data.get("status")).isEqualTo("OCCUPATA");
    }

    @Test
    void disponibilitaWithMalformedDateReturns400() throws Exception {
        ResponseEntity<String> resp = get(
                "/api/prenotazioni/disponibilita?aulaId=" + aulaId + "&inizio=non-una-data&fine=nemmeno");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(asMap(resp.getBody()).get("error")).isEqualTo("INVALID_START_DATE");
    }

    /**
     * Regressione: "/stato/{aulaId}" e "/stato/{stato}" erano dichiarati sullo stesso
     * pattern di path, quindi a runtime Spring falliva con "Ambiguous handler methods
     * mapped" e ENTRAMBI gli endpoint rispondevano 500. Lo stato aula vive ora su
     * "/stato-aula/{aulaId}"; questi due test difendono la separazione.
     */
    @Test
    void statoAulaReturnsRoomStatusPayload() throws Exception {
        ResponseEntity<String> resp = get("/api/prenotazioni/stato-aula/" + aulaId);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = asMap(resp.getBody());
        assertThat(body.keySet()).containsExactlyInAnyOrder("aulaId", "stato", "timestamp");
        assertThat(body.get("aulaId")).isEqualTo(aulaId.intValue());
    }

    @Test
    void prenotazioniByStatoReturnsFilteredList() throws Exception {
        ResponseEntity<String> resp = get("/api/prenotazioni/stato/prenotata");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = asMap(resp.getBody());
        assertThat(body.keySet()).containsExactlyInAnyOrder("stato", "prenotazioni", "totalPrenotazioni");
        assertThat(body.get("stato")).isEqualTo("prenotata");
        assertThat(body.get("totalPrenotazioni")).isEqualTo(1);
    }

    @Test
    void prenotazioniByStatoWithNoMatchReturnsEmptyList() throws Exception {
        ResponseEntity<String> resp = get("/api/prenotazioni/stato/annullata");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(asMap(resp.getBody()).get("totalPrenotazioni")).isEqualTo(0);
    }

    @Test
    void queryEndpointsRequireAuthentication() {
        for (String url : new String[]{
                "/api/prenotazioni/mie",
                "/api/prenotazioni/future",
                "/api/prenotazioni/all-details"}) {
            ResponseEntity<String> resp = rest.exchange(url, HttpMethod.GET, HttpEntity.EMPTY, String.class);
            assertThat(resp.getStatusCode())
                    .as("endpoint %s senza token", url)
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object o) {
        return (Map<String, Object>) o;
    }
}
