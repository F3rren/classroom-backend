package com.prenotazioni;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prenotazioni.testsupport.TestJwt;
import com.prenotazioni.model.Aula;
import com.prenotazioni.model.StatoAula;
import com.prenotazioni.model.Prenotazione;
import com.prenotazioni.model.StatoPrenotazione;
import com.prenotazioni.model.Ruolo;
import com.prenotazioni.model.ProprietarioPrenotazione;
import com.prenotazioni.repository.IAulaRepository;
import com.prenotazioni.repository.IPrenotazioneRepository;
import com.prenotazioni.eventi.PrenotazioneCancellataEvento;
import com.prenotazioni.messaggistica.PubblicatoreEventi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

/**
 * Copre gli endpoint admin finora senza test: gestione aule (GET/PUT/DELETE su
 * /api/admin/rooms), eliminazione utente e gestione prenotazioni lato admin.
 *
 * Per ogni operazione distruttiva il test non si ferma allo status code ma verifica
 * l'effetto reale sul database, ed esiste sempre la controprova che un utente non
 * admin riceve 403 sullo stesso endpoint.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AdminManagementTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private IAulaRepository aulaRepository;

    @Autowired
    private IPrenotazioneRepository prenotazioneRepository;

    @Autowired
    /**
     * La notifica non e' piu' una riga scritta in questo processo ma una chiamata a
     * notifica-service. Il test conserva il proprio intento verificando che la chiamata
     * parta: e' il confine giusto da controllare da qui, e non richiede che l'altro
     * servizio sia in esecuzione.
     */
    @MockBean
    private PubblicatoreEventi pubblicatoreEventi;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String tokenAdmin;
    private String tokenUser;
    private Long aulaId;
    private Long utenteNormaleId;
    private Long prenotazioneId;

    /** Gli id non arrivano piu' da un insert: li sceglie il test e li firma nel token. */
    private static final Long ID_ADMIN = 1L;
    private static final Long ID_UTENTE_NORMALE = 2L;

    @BeforeEach
    void setUp() {
        prenotazioneRepository.deleteAll();
        aulaRepository.deleteAll();

        // Nessun utente da creare: la tabella utenti appartiene ad auth-service e i token
        // sono firmati in locale con lo stesso segreto (vedi TestJwt).
        utenteNormaleId = ID_UTENTE_NORMALE;

        Aula aula = new Aula();
        aula.setNome("Aula Admin");
        aula.setPiano(1);
        aula.setCapienza(25);
        aula.setVirtual(false);
        aula.setStato(StatoAula.LIBERA);
        aulaId = aulaRepository.save(aula).getId();

        Prenotazione p = new Prenotazione();
        p.setAula(aula);
        p.setUtente(new ProprietarioPrenotazione(utenteNormaleId, "user-mgmt", "User Mgmt"));
        p.setInizio(LocalDateTime.now().plusDays(3).withNano(0));
        p.setFine(LocalDateTime.now().plusDays(3).plusHours(2).withNano(0));
        p.setStato(StatoPrenotazione.PRENOTATA);
        p.setDescrizione("Prenotazione gestita da admin");
        p.setDataCreazione(LocalDateTime.now());
        prenotazioneId = prenotazioneRepository.save(p).getId();

        tokenAdmin = TestJwt.perAdmin(ID_ADMIN, "admin-mgmt@test.it");
        tokenUser = TestJwt.perUtente(ID_UTENTE_NORMALE, "user-mgmt@test.it", "User Mgmt");
    }


    @SuppressWarnings("unchecked")
    private HttpHeaders bearer(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private ResponseEntity<String> exchange(String url, HttpMethod method, String token, Object body) {
        HttpEntity<Object> entity = body == null
                ? new HttpEntity<>(bearer(token))
                : new HttpEntity<>(body, bearer(token));
        return rest.exchange(url, method, entity, String.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(String json) throws Exception {
        return objectMapper.readValue(json, Map.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> dataOf(ResponseEntity<String> resp) throws Exception {
        return (Map<String, Object>) asMap(resp.getBody()).get("data");
    }

    // ==================== Gestione aule ====================

    @Test
    void adminListsRooms() throws Exception {
        ResponseEntity<String> resp = exchange("/api/admin/rooms", HttpMethod.GET, tokenAdmin, null);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dataOf(resp).get("totalRooms")).isEqualTo(1);
    }

    @Test
    void adminGetsSingleRoomWrappedInRoomKey() throws Exception {
        ResponseEntity<String> resp = exchange("/api/admin/rooms/" + aulaId, HttpMethod.GET, tokenAdmin, null);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dataOf(resp).keySet()).containsExactly("room");
    }

    @Test
    void adminGetsRoomNotFound() throws Exception {
        ResponseEntity<String> resp = exchange("/api/admin/rooms/999999", HttpMethod.GET, tokenAdmin, null);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(asMap(resp.getBody()).get("error")).isEqualTo("ROOM_NOT_FOUND");
    }

    @Test
    void adminUpdatesRoomAndChangeIsPersisted() throws Exception {
        Map<String, Object> body = Map.of("nome", "Aula Rinominata", "capienza", 42, "piano", 4);
        ResponseEntity<String> resp = exchange("/api/admin/rooms/" + aulaId, HttpMethod.PUT, tokenAdmin, body);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dataOf(resp).get("nome")).isEqualTo("Aula Rinominata");

        Aula ricaricata = aulaRepository.findById(aulaId).orElseThrow();
        assertThat(ricaricata.getNome()).isEqualTo("Aula Rinominata");
        assertThat(ricaricata.getCapienza()).isEqualTo(42);
    }

    @Test
    void adminUpdateRoomRejectsInvalidPayload() {
        // capienza negativa viola @Positive su AulaRequest
        Map<String, Object> body = Map.of("nome", "X", "capienza", -5, "piano", 1);
        ResponseEntity<String> resp = exchange("/api/admin/rooms/" + aulaId, HttpMethod.PUT, tokenAdmin, body);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void adminDeletesRoomAndItDisappears() {
        prenotazioneRepository.deleteAll(); // l'aula ha una prenotazione collegata
        ResponseEntity<String> resp = exchange("/api/admin/rooms/" + aulaId, HttpMethod.DELETE, tokenAdmin, null);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(aulaRepository.existsById(aulaId)).isFalse();
    }

    @Test
    void adminDeleteRoomNotFoundReturns404() {
        ResponseEntity<String> resp = exchange("/api/admin/rooms/999999", HttpMethod.DELETE, tokenAdmin, null);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ==================== Gestione utenti ====================

    // ==================== Gestione prenotazioni ====================

    @Test
    void adminListsAllBookingsWithStats() throws Exception {
        ResponseEntity<String> resp = exchange("/api/admin/prenotazioni", HttpMethod.GET, tokenAdmin, null);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> data = dataOf(resp);
        assertThat(data.keySet()).containsExactlyInAnyOrder("prenotazioni", "statistiche");

        @SuppressWarnings("unchecked")
        Map<String, Object> stats = (Map<String, Object>) data.get("statistiche");
        assertThat(stats.keySet()).containsExactlyInAnyOrder("totale", "attive", "annullate");
        assertThat(stats.get("totale")).isEqualTo(1);
        assertThat(stats.get("attive")).isEqualTo(1);
        assertThat(stats.get("annullate")).isEqualTo(0);
    }

    @Test
    void adminForceDeletesAnyBookingAndNotifiesOwner() throws Exception {
        ResponseEntity<String> resp = exchange(
                "/api/admin/prenotazioni/" + prenotazioneId, HttpMethod.DELETE, tokenAdmin,
                Map.of("reason", "Aula richiesta per un esame"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> data = dataOf(resp);
        assertThat(data.get("adminAction")).isEqualTo(true);
        assertThat(data.get("reason")).isEqualTo("Aula richiesta per un esame");

        // la prenotazione risulta annullata e il proprietario riceve una notifica
        Prenotazione dopo = prenotazioneRepository.findById(prenotazioneId).orElseThrow();
        assertThat(dopo.getStato()).isEqualTo(StatoPrenotazione.ANNULLATA);
        verify(pubblicatoreEventi).pubblicaCancellazione(any(PrenotazioneCancellataEvento.class));
    }

    @Test
    void anOverlongReasonIsRejectedInsteadOfSilentlyLosingTheNotification() throws Exception {
        // Il motivo finisce concatenato dentro Notifica.messaggio, che e' varchar(1000).
        // Senza un limite, il salvataggio della notifica esplode e AdminController inghiotte
        // l'eccezione ("non blocchiamo l'operazione se la notifica fallisce"): la prenotazione
        // risulta annullata ma il proprietario non viene MAI avvisato, in silenzio.
        String motivoEnorme = "x".repeat(1500);

        ResponseEntity<String> resp = exchange(
                "/api/admin/prenotazioni/" + prenotazioneId, HttpMethod.DELETE, tokenAdmin,
                Map.of("reason", motivoEnorme));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        // e la prenotazione NON deve essere stata annullata da una richiesta rifiutata
        assertThat(prenotazioneRepository.findById(prenotazioneId).orElseThrow().getStato())
                .isEqualTo(StatoPrenotazione.PRENOTATA);
    }

    @Test
    void aReasonWithinTheLimitStillNotifiesTheOwner() {
        String motivoLungoMaValido = "y".repeat(400);

        ResponseEntity<String> resp = exchange(
                "/api/admin/prenotazioni/" + prenotazioneId, HttpMethod.DELETE, tokenAdmin,
                Map.of("reason", motivoLungoMaValido));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        // la notifica deve esistere davvero, non essere persa da un catch silenzioso
        verify(pubblicatoreEventi).pubblicaCancellazione(any(PrenotazioneCancellataEvento.class));
    }

    @Test
    void adminForceDeleteWorksWithoutBody() {
        // il corpo con il motivo e' opzionale: senza, si usa un motivo di default
        ResponseEntity<String> resp = exchange(
                "/api/admin/prenotazioni/" + prenotazioneId, HttpMethod.DELETE, tokenAdmin, null);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void adminForceDeleteOnMissingBookingReturns404() {
        ResponseEntity<String> resp = exchange(
                "/api/admin/prenotazioni/999999", HttpMethod.DELETE, tokenAdmin, null);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ==================== Controprova: nessun accesso senza ruolo admin ====================

    @Test
    void nonAdminIsForbiddenOnEveryAdminEndpoint() {
        record Chiamata(String url, HttpMethod method) {}
        Chiamata[] chiamate = {
                new Chiamata("/api/admin/rooms", HttpMethod.GET),
                new Chiamata("/api/admin/rooms/" + aulaId, HttpMethod.GET),
                new Chiamata("/api/admin/rooms/" + aulaId, HttpMethod.DELETE),
                new Chiamata("/api/admin/prenotazioni", HttpMethod.GET),
                new Chiamata("/api/admin/prenotazioni/" + prenotazioneId, HttpMethod.DELETE),
                // /api/admin/delete/{id} non e' piu' servito da questo servizio:
                // la gestione utenti e' passata ad auth-service.
        };

        for (Chiamata c : chiamate) {
            ResponseEntity<String> resp = exchange(c.url(), c.method(), tokenUser, null);
            assertThat(resp.getStatusCode())
                    .as("%s %s con token non-admin", c.method(), c.url())
                    .isEqualTo(HttpStatus.FORBIDDEN);
        }

        // e nulla e' stato modificato
        assertThat(aulaRepository.existsById(aulaId)).isTrue();
    }

    // ==================== Elenco utenti ====================

    @Test
    void adminCreateRoomRejectsDuplicateName() throws Exception {
        Map<String, Object> body = Map.of("nome", "Aula Admin", "capienza", 10, "piano", 1);

        ResponseEntity<String> resp = exchange("/api/admin/createrooms", HttpMethod.POST, tokenAdmin, body);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(asMap(resp.getBody()).get("error")).isEqualTo("ROOM_CREATION_FAILED");
    }

    @Test
    void adminUpdateRoomWithInvalidIdIsRejected() throws Exception {
        Map<String, Object> body = Map.of("nome", "Qualsiasi", "capienza", 10, "piano", 1);

        ResponseEntity<String> resp = exchange("/api/admin/rooms/0", HttpMethod.PUT, tokenAdmin, body);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(asMap(resp.getBody()).get("error")).isEqualTo("INVALID_ROOM_ID");
    }

    @Test
    void adminUpdateRoomNotFoundReturns404() throws Exception {
        Map<String, Object> body = Map.of("nome", "Inesistente", "capienza", 10, "piano", 1);

        ResponseEntity<String> resp = exchange("/api/admin/rooms/999999", HttpMethod.PUT, tokenAdmin, body);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(asMap(resp.getBody()).get("error")).isEqualTo("ROOM_UPDATE_FAILED");
    }

    @Test
    void adminEndpointsRequireAuthentication() {
        ResponseEntity<String> resp = rest.exchange(
                "/api/admin/rooms", HttpMethod.GET, HttpEntity.EMPTY, String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

}
