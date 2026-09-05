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
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression suite per il fix IDOR/leak-password su /api/bookings.
 * Owner (A) crea una prenotazione; Other (B), senza alcun rapporto con essa,
 * non deve poterla leggere ne' vederne la password in chiaro.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
// Isola il contesto (e quindi lo schema H2) da questa classe: senza, dati lasciati da
// altre classi di test @SpringBootTest nello stesso DB in-memory condiviso possono violare
// vincoli FK qui (es. un Utente referenziato da una Notifica creata da un'altra classe).
class BookingControllerTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private BookingRepository bookingRepository;

    private Long prenotazioneIdDiOwner;
    private Long roomId;
    private String tokenOwner;
    private String tokenOther;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        roomRepository.deleteAll();

        BookingOwner owner = nuovoUtente(1L, "owner", "Owner Test");

        BookingOwner other = nuovoUtente(2L, "other", "Other Test");

        Room room = new Room();
        room.setName("Aula IT Test");
        room.setFloor(1);
        room.setCapacity(20);
        room.setVirtual(false);
        room.setStatus(RoomStatus.LIBERA);
        roomRepository.save(room);
        roomId = room.getId();

        Booking booking = new Booking();
        booking.setRoom(room);
        booking.setUser(owner);
        booking.setStartTime(LocalDateTime.now().plusDays(1));
        booking.setEndTime(LocalDateTime.now().plusDays(1).plusHours(2));
        booking.setStatus(BookingStatus.PRENOTATA);
        booking.setDescription("Riunione privata di owner");
        booking.setCreatedAt(LocalDateTime.now());
        bookingRepository.save(booking);
        prenotazioneIdDiOwner = booking.getId();

        tokenOwner = TestJwt.perUtente(1L, "owner@test.it", "Owner Test");
        tokenOther = TestJwt.perUtente(2L, "other@test.it", "Other Test");
    }

    /** L'istantanea di un proprietario. Prima creava un utente vero: la tabella non e' piu' qui. */
    private BookingOwner nuovoUtente(Long id, String username, String name) {
        return new BookingOwner(id, username, name);
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
                "/api/bookings/" + prenotazioneIdDiOwner,
                HttpMethod.GET,
                new HttpEntity<>(bearer(tokenOther)),
                String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resp.getBody()).doesNotContain("Riunione privata di owner");
    }

    @Test
    void otherUserCannotReadOwnersBookingDetails() {
        ResponseEntity<String> resp = rest.exchange(
                "/api/bookings/" + prenotazioneIdDiOwner + "/details",
                HttpMethod.GET,
                new HttpEntity<>(bearer(tokenOther)),
                String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void ilProprietarioPuoLeggereLaPropriaPrenotazione() {
        ResponseEntity<String> resp = rest.exchange(
                "/api/bookings/" + prenotazioneIdDiOwner,
                HttpMethod.GET,
                new HttpEntity<>(bearer(tokenOwner)),
                String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).contains("Riunione privata di owner");
    }

    @Test
    void passwordIsNeverSerializedInAnyPrenotazioneResponse() {
        ResponseEntity<String> ownerView = rest.exchange(
                "/api/bookings/" + prenotazioneIdDiOwner,
                HttpMethod.GET,
                new HttpEntity<>(bearer(tokenOwner)),
                String.class);
        assertThat(ownerView.getBody()).doesNotContain("password-owner");
        assertThat(ownerView.getBody()).doesNotContain("\"password\"");

        ResponseEntity<String> listView = rest.exchange(
                "/api/bookings",
                HttpMethod.GET,
                new HttpEntity<>(bearer(tokenOther)),
                String.class);
        assertThat(listView.getBody()).doesNotContain("password-owner");
        assertThat(listView.getBody()).doesNotContain("\"password\"");
    }

    @Test
    void listEndpointHidesOwnerPiiFromOtherAuthenticatedUsers() {
        ResponseEntity<String> resp = rest.exchange(
                "/api/bookings",
                HttpMethod.GET,
                new HttpEntity<>(bearer(tokenOther)),
                String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).doesNotContain("owner@test.it");
    }

    // ==================== SHAPE-LOCK: blocca derive accidentali di forma durante il refactor Swagger ====================


    // La forma della risposta di login e' verificata in auth-service, che ora possiede
    // /api/auth/login: da qui quell'endpoint risponde 404.

    @Test
    void prenotaSuccessResponseShapeIsLocked() throws Exception {
        HttpHeaders headers = bearer(tokenOwner);
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        Map<String, Object> body = Map.of(
                "roomId", roomId,
                "startTime", LocalDateTime.now().plusDays(2).toString(),
                "endTime", LocalDateTime.now().plusDays(2).plusHours(1).toString());

        ResponseEntity<String> resp = rest.exchange(
                "/api/bookings/book",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> responseBody = TestJson.comeMappa(resp.getBody());
        assertThat(responseBody.keySet()).containsExactlyInAnyOrder(
                "success", "message", "data", "timestamp", "sessionId");

        Map<String, Object> data = (Map<String, Object>) responseBody.get("data");
        assertThat(data.keySet()).containsExactlyInAnyOrder("prenotazione", "roomId", "periodo");
    }

    @Test
    void missingFieldErrorResponseShapeIsLocked() throws Exception {
        HttpHeaders headers = bearer(tokenOwner);
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        // aulaId mancante
        Map<String, Object> body = Map.of(
                "startTime", LocalDateTime.now().plusDays(2).toString(),
                "endTime", LocalDateTime.now().plusDays(2).plusHours(1).toString());

        ResponseEntity<String> resp = rest.exchange(
                "/api/bookings/book",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Map<String, Object> responseBody = TestJson.comeMappa(resp.getBody());
        assertThat(responseBody.keySet()).containsExactlyInAnyOrder(
                "success", "error", "message", "userMessage", "timestamp", "sessionId");
        assertThat(responseBody.get("success")).isEqualTo(false);
    }

    // ==================== Annullamento: regressione doppio annullamento ====================

    @Test
    void ilProprietarioPuoAnnullareLaPropriaPrenotazione() {
        ResponseEntity<String> resp = rest.exchange(
                "/api/bookings/" + prenotazioneIdDiOwner, HttpMethod.DELETE,
                new HttpEntity<>(bearer(tokenOwner)), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bookingRepository.findById(prenotazioneIdDiOwner).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.ANNULLATA);
    }

    @Test
    void annullareDueVolteVieneRifiutatoInveceCheRiuscireInSilenzio() throws Exception {
        rest.exchange("/api/bookings/" + prenotazioneIdDiOwner, HttpMethod.DELETE,
                new HttpEntity<>(bearer(tokenOwner)), String.class);

        ResponseEntity<String> secondo = rest.exchange(
                "/api/bookings/" + prenotazioneIdDiOwner, HttpMethod.DELETE,
                new HttpEntity<>(bearer(tokenOwner)), String.class);

        assertThat(secondo.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(TestJson.comeMappa(secondo.getBody()).get("error")).isEqualTo("INVALID_STATE");
    }

    @Test
    void strangerCannotCancelSomeoneElsesBooking() throws Exception {
        ResponseEntity<String> resp = rest.exchange(
                "/api/bookings/" + prenotazioneIdDiOwner, HttpMethod.DELETE,
                new HttpEntity<>(bearer(tokenOther)), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(TestJson.comeMappa(resp.getBody()).get("error")).isEqualTo("ACCESS_DENIED");
        // la prenotazione resta intatta
        assertThat(bookingRepository.findById(prenotazioneIdDiOwner).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.PRENOTATA);
    }

    @Test
    void annullareUnaPrenotazioneInesistenteRisponde404() {
        ResponseEntity<String> resp = rest.exchange(
                "/api/bookings/999999", HttpMethod.DELETE,
                new HttpEntity<>(bearer(tokenOwner)), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

}
