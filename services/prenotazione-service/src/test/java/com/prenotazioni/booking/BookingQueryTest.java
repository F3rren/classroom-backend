package com.prenotazioni.booking;

import com.prenotazioni.testsupport.TestJson;
import com.prenotazioni.testsupport.TestJwt;
import com.prenotazioni.booking.model.Room;
import com.prenotazioni.booking.model.RoomStatus;
import com.prenotazioni.booking.model.Booking;
import com.prenotazioni.booking.model.BookingStatus;
import com.prenotazioni.booking.model.BookingOwner;
import com.prenotazioni.booking.repository.RoomRepository;
import com.prenotazioni.booking.repository.BookingRepository;
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
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Copre gli endpoint di sola lettura di /api/bookings finora senza test:
 * /mie, /future, /all-details, /disponibilita, /{id}/details e /stato/{...}.
 *
 * Il valore qui non e' solo "risponde 200": ogni test blocca anche l'esatto set di
 * chiavi JSON, perche' alcuni di questi endpoint NON sono avvolti nell'envelope
 * ApiEnvelope (shape storica preservata per il frontend) e la differenza va difesa.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class BookingQueryTest {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private BookingRepository bookingRepository;

    private String token;
    private Long roomId;
    private Long bookingId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        roomRepository.deleteAll();


        Room room = new Room();
        room.setName("Aula Query");
        room.setFloor(2);
        room.setCapacity(30);
        room.setVirtual(false);
        room.setStatus(RoomStatus.FREE);
        roomId = roomRepository.save(room).getId();

        startTime = LocalDateTime.now().plusDays(2).withNano(0);
        endTime = startTime.plusHours(2);

        BookingOwner user = new BookingOwner(1L, "query-user", "Query User");

        Booking p = new Booking();
        p.setRoom(room);
        p.setUser(user);
        p.setStartTime(startTime);
        p.setEndTime(endTime);
        p.setStatus(BookingStatus.BOOKED);
        p.setDescription("Prenotazione per test di query");
        p.setCreatedAt(LocalDateTime.now());
        bookingId = bookingRepository.save(p).getId();

        token = TestJwt.perUtente(1L, "prenotazionequerytest@test.it", "Utente Test");
    }

    @SuppressWarnings("unchecked")
    private HttpHeaders bearer() {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return h;
    }

    private ResponseEntity<String> get(String url) {
        return rest.exchange(url, HttpMethod.GET, new HttpEntity<>(bearer()), String.class);
    }


    @Test
    void miePrenotazioniTornaSoloLeProprieSenzaBusta() throws Exception {
        ResponseEntity<String> resp = get("/api/bookings/mine");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = TestJson.comeMappa(resp.getBody());
        // shape storica: solo "bookings", nessun envelope e nessun totale
        assertThat(body.keySet()).containsExactly("bookings");
        assertThat((java.util.List<?>) body.get("bookings")).hasSize(1);
    }

    @Test
    void miePrenotazioniEscludeLeAnnullate() throws Exception {
        Booking p = bookingRepository.findById(bookingId).orElseThrow();
        p.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(p);

        Map<String, Object> body = TestJson.comeMappa(get("/api/bookings/mine").getBody());
        assertThat((java.util.List<?>) body.get("bookings")).isEmpty();
    }

    @Test
    void lePrenotazioniFutureIncludonoIlTotale() throws Exception {
        ResponseEntity<String> resp = get("/api/bookings/future");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = TestJson.comeMappa(resp.getBody());
        assertThat(body.keySet()).containsExactlyInAnyOrder("bookings", "totalBookings");
        assertThat(body.get("totalBookings")).isEqualTo(1);
    }

    @Test
    void lePrenotazioniFutureNonEspongonoIDatiDelProprietario() {
        // La lista e' visibile a qualunque utente autenticato: il proprietario va
        // ridotto a id/username/nome, mai email o ruolo (sanitizeOwnerForListing).
        ResponseEntity<String> resp = get("/api/bookings/future");

        assertThat(resp.getBody()).doesNotContain("query-user@test.it");
        assertThat(resp.getBody()).doesNotContain("query-password");
    }

    @Test
    void ilDettaglioCompletoTornaLaListaTipizzata() throws Exception {
        ResponseEntity<String> resp = get("/api/bookings/all-details");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = TestJson.comeMappa(resp.getBody());
        assertThat(body.keySet()).containsExactlyInAnyOrder("bookings", "totalBookings");
        assertThat(body.get("totalBookings")).isEqualTo(1);
    }

    @Test
    void prenotazioneDetailsByIdReturnsDettagli() throws Exception {
        ResponseEntity<String> resp = get("/api/bookings/" + bookingId + "/details");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = TestJson.comeMappa(resp.getBody());
        assertThat(body.keySet()).containsExactlyInAnyOrder(
                "prenotazione", "dettagliCompleti", "totalDettagli");
    }

    @Test
    void laDisponibilitaSegnalaLiberaUnaFasciaLibera() throws Exception {
        String libero = startTime.plusDays(5).format(ISO);
        String liberoFine = startTime.plusDays(5).plusHours(1).format(ISO);

        ResponseEntity<String> resp = get(
                "/api/bookings/availability?roomId=" + roomId + "&start=" + libero + "&end=" + liberoFine);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> data = castMap(TestJson.comeMappa(resp.getBody()).get("data"));
        assertThat(data.keySet()).containsExactlyInAnyOrder("roomId", "disponibile", "periodo", "status");
        assertThat(data.get("disponibile")).isEqualTo(true);
        assertThat(data.get("status")).isEqualTo("FREE");
    }

    @Test
    void laDisponibilitaSegnalaOccupataUnaFasciaPrenotata() throws Exception {
        ResponseEntity<String> resp = get(
                "/api/bookings/availability?roomId=" + roomId
                        + "&start=" + startTime.format(ISO) + "&end=" + endTime.format(ISO));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> data = castMap(TestJson.comeMappa(resp.getBody()).get("data"));
        assertThat(data.get("disponibile")).isEqualTo(false);
        assertThat(data.get("status")).isEqualTo("BUSY");
    }

    @Test
    void laDisponibilitaConUnaDataMalformataRisponde400() throws Exception {
        ResponseEntity<String> resp = get(
                "/api/bookings/availability?roomId=" + roomId + "&start=non-una-data&end=nemmeno");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(TestJson.comeMappa(resp.getBody()).get("error")).isEqualTo("INVALID_START_DATE");
    }

    /**
     * Regressione: "/stato/{aulaId}" e "/stato/{stato}" erano dichiarati sullo stesso
     * pattern di path, quindi a runtime Spring falliva con "Ambiguous handler methods
     * mapped" e ENTRAMBI gli endpoint rispondevano 500. Lo stato aula vive ora su
     * "/stato-aula/{aulaId}"; questi due test difendono la separazione.
     */
    @Test
    void statoAulaReturnsRoomStatusPayload() throws Exception {
        ResponseEntity<String> resp = get("/api/bookings/room-status/" + roomId);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = TestJson.comeMappa(resp.getBody());
        assertThat(body.keySet()).containsExactlyInAnyOrder("roomId", "status", "timestamp");
        assertThat(body.get("roomId")).isEqualTo(roomId.intValue());
    }

    @Test
    void ilFiltroPerStatoTornaSoloLePrenotazioniCorrispondenti() throws Exception {
        ResponseEntity<String> resp = get("/api/bookings/status/booked");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = TestJson.comeMappa(resp.getBody());
        assertThat(body.keySet()).containsExactlyInAnyOrder("status", "bookings", "totalBookings");
        assertThat(body.get("status")).isEqualTo("booked");
        assertThat(body.get("totalBookings")).isEqualTo(1);
    }

    @Test
    void prenotazioniByStatoWithNoMatchReturnsEmptyList() throws Exception {
        ResponseEntity<String> resp = get("/api/bookings/status/cancelled");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(TestJson.comeMappa(resp.getBody()).get("totalBookings")).isEqualTo(0);
    }

    @Test
    void gliEndpointDiInterrogazioneRichiedonoAutenticazione() {
        for (String url : new String[]{
                "/api/bookings/mine",
                "/api/bookings/future",
                "/api/bookings/all-details"}) {
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


    @Test
    void unaPrenotazioneInesistenteRispondeNellaFormaComune() throws Exception {
        // Questi endpoint rispondevano {"error":"Prenotazione non trovata"}: una forma
        // tutta loro, senza "success" ne' "userMessage", con "error" che conteneva una
        // frase invece di un codice. Nessun test lo copriva, ed e' il motivo per cui la
        // divergenza e' sopravvissuta cosi' a lungo.
        ResponseEntity<String> resp = get("/api/bookings/999999");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        Map<String, Object> body = TestJson.comeMappa(resp.getBody());
        assertThat(body.get("success")).isEqualTo(false);
        assertThat(body.get("error")).isEqualTo("PRENOTAZIONE_NOT_FOUND");
        assertThat(body.get("userMessage")).isNotNull();
        assertThat(body.get("sessionId")).isNotNull();
    }

    @Test
    void ancheIDettagliDiUnaPrenotazioneInesistenteUsanoLaFormaComune() throws Exception {
        ResponseEntity<String> resp = get("/api/bookings/999999/details");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(TestJson.comeMappa(resp.getBody()).get("error")).isEqualTo("PRENOTAZIONE_NOT_FOUND");
    }

    @Test
    void unoStatoInesistenteRispondeNellaFormaComuneEDiceQualiSonoAmmessi() throws Exception {
        ResponseEntity<String> resp = get("/api/bookings/status/inventato");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Map<String, Object> body = TestJson.comeMappa(resp.getBody());
        assertThat(body.get("success")).isEqualTo(false);
        // L'elenco degli stati ammessi si ricava dall'enum: se ne aggiungessero uno e il
        // messaggio restasse indietro, questo assert se ne accorgerebbe.
        assertThat(String.valueOf(body.get("userMessage"))).contains("booked", "cancelled");
    }
}
