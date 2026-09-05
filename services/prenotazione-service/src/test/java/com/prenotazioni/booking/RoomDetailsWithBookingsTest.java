package com.prenotazioni.booking;

import com.prenotazioni.testsupport.TestJson;
import com.prenotazioni.testsupport.TestJwt;
import com.prenotazioni.booking.model.Room;
import com.prenotazioni.booking.model.RoomStatus;
import com.prenotazioni.booking.model.Booking;
import com.prenotazioni.booking.model.BookingStatus;
import com.prenotazioni.model.Role;
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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AulaService contiene lo stesso blocco "dettagli aula" clonato tre volte
 * (getAllRoomsWithDetails, getRoomWithDetails e il privato getRoomsDetailsFromList).
 * Tutti e tre erano gia' invocati da RoomQueryTest, ma la sua fixture non crea nessuna
 * prenotazione: percio' ogni ramo per-prenotazione (occupata adesso, bloccata, in
 * manutenzione, imminente, elenco prenotazioni) non veniva mai eseguito.
 *
 * Questa classe fornisce la fixture mancante. Le prenotazioni sono inserite via
 * repository e non via HTTP, perche' il controller rifiuta le date nel passato e qui
 * servono prenotazioni che attraversano ADESSO.
 *
 * Ogni aula copre un solo scenario: i blocchi fanno break sulla prima prenotazione
 * sovrapposta, quindi mettere piu' casi sulla stessa aula ne nasconderebbe alcuni.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RoomDetailsWithBookingsTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private BookingRepository bookingRepository;

    private String token;
    private Long aulaOccupataId;
    private Long aulaBloccataId;
    private Long aulaManutenzioneId;
    private Long aulaImminenteId;
    private Long aulaLiberaId;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        roomRepository.deleteAll();

        // Il nome finisce dentro currentBooking, quindi conta che sia valorizzato.
        BookingOwner user = new BookingOwner(1L, "dettagli", "Mario Rossi");

        LocalDateTime ora = LocalDateTime.now();

        aulaOccupataId = salvaAula("Aula Occupata", 1, 30, false);
        prenota(aulaOccupataId, user, BookingStatus.PRENOTATA, ora.minusHours(1), ora.plusHours(1), "Lezione di Analisi");

        aulaBloccataId = salvaAula("Aula Bloccata", 1, 30, false);
        prenota(aulaBloccataId, user, BookingStatus.BLOCCATA, ora.minusHours(1), ora.plusHours(1), "Evento riservato");

        aulaManutenzioneId = salvaAula("Aula Manutenzione", 2, 20, false);
        prenota(aulaManutenzioneId, user, BookingStatus.MANUTENZIONE, ora.minusHours(1), ora.plusHours(1), "Sostituzione proiettore");

        // Aula virtuale con prenotazione IMMINENTE (entro 2 ore, ma non ancora iniziata):
        // copre il secondo ramo e il lato virtuale del terzo clone.
        aulaImminenteId = salvaAula("Aula Virtuale Imminente", 0, 50, true);
        prenota(aulaImminenteId, user, BookingStatus.PRENOTATA, ora.plusMinutes(30), ora.plusMinutes(90), null);

        aulaLiberaId = salvaAula("Aula Libera", 3, 10, false);

        token = TestJwt.perUtente(1L, "roomdetailswithbookingstest@test.it", "Utente Test");
    }

    private Long salvaAula(String nome, int piano, int capienza, boolean virtuale) {
        Room a = new Room();
        a.setNome(nome);
        a.setPiano(piano);
        a.setCapienza(capienza);
        a.setVirtual(virtuale);
        a.setStato(RoomStatus.LIBERA);
        return roomRepository.save(a).getId();
    }

    private void prenota(Long roomId, BookingOwner user, BookingStatus status,
                         LocalDateTime inizio, LocalDateTime fine, String descrizione) {
        Booking p = new Booking();
        p.setAula(roomRepository.findById(roomId).orElseThrow());
        p.setUtente(user);
        p.setInizio(inizio);
        p.setFine(fine);
        p.setStato(status);
        p.setDescrizione(descrizione);
        p.setDataCreazione(LocalDateTime.now()); // letto da blockInfo.blockedAt
        bookingRepository.save(p);
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<String> get(String url) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return rest.exchange(url, HttpMethod.GET, new HttpEntity<>(h), String.class);
    }


    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> roomsOf(ResponseEntity<String> resp) throws Exception {
        Map<String, Object> data = (Map<String, Object>) TestJson.comeMappa(resp.getBody()).get("data");
        return (List<Map<String, Object>>) data.get("rooms");
    }

    private Map<String, Object> byName(List<Map<String, Object>> rooms, String nome) {
        return rooms.stream()
                .filter(r -> nome.equals(r.get("name")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("aula non trovata nella risposta: " + nome));
    }

    // ==================== clone 1: /api/rooms/detailed ====================

    @Test
    void ilDettaglioSegnalaLAulaOccupataAdesso() throws Exception {
        List<Map<String, Object>> rooms = roomsOf(get("/api/rooms/detailed"));
        Map<String, Object> occupata = byName(rooms, "Aula Occupata");

        assertThat(occupata.get("status")).isEqualTo("prenotata");

        @SuppressWarnings("unchecked")
        Map<String, Object> booking = (Map<String, Object>) occupata.get("booking");
        assertThat(booking).isNotNull();
        assertThat(booking.get("user")).isEqualTo("Mario Rossi");
        assertThat(booking.get("purpose")).isEqualTo("Lezione di Analisi");
        assertThat((String) booking.get("time")).contains("-");
    }

    @Test
    void ilDettaglioSegnalaLAulaBloccataConIDatiDelBlocco() throws Exception {
        List<Map<String, Object>> rooms = roomsOf(get("/api/rooms/detailed"));
        Map<String, Object> bloccata = byName(rooms, "Aula Bloccata");

        assertThat(bloccata.get("status")).isEqualTo("bloccata");

        @SuppressWarnings("unchecked")
        Map<String, Object> blocked = (Map<String, Object>) bloccata.get("blocked");
        assertThat(blocked).isNotNull();
        assertThat(blocked.get("reason")).isEqualTo("Evento riservato");
        assertThat(blocked.get("blockedBy")).isEqualTo("admin");
    }

    @Test
    void ilDettaglioTrattaLaManutenzioneComeBlocco() throws Exception {
        List<Map<String, Object>> rooms = roomsOf(get("/api/rooms/detailed"));
        Map<String, Object> manutenzione = byName(rooms, "Aula Manutenzione");

        assertThat(manutenzione.get("status")).isEqualTo("bloccata");
        assertThat(manutenzione.get("blocked")).isNotNull();
    }

    @Test
    void ilDettaglioSegnalaUnaPrenotazioneEntroDueOre() throws Exception {
        List<Map<String, Object>> rooms = roomsOf(get("/api/rooms/detailed"));
        Map<String, Object> imminente = byName(rooms, "Aula Virtuale Imminente");

        // non e' occupata adesso, ma inizia entro 2 ore -> comunque "prenotata"
        assertThat(imminente.get("status")).isEqualTo("prenotata");

        @SuppressWarnings("unchecked")
        Map<String, Object> booking = (Map<String, Object>) imminente.get("booking");
        assertThat(booking).isNotNull();
        // descrizione null -> il fallback "Lezione"
        assertThat(booking.get("purpose")).isEqualTo("Lezione");
    }

    @Test
    void ilDettaglioLasciaLiberaUnAulaSenzaPrenotazioni() throws Exception {
        List<Map<String, Object>> rooms = roomsOf(get("/api/rooms/detailed"));
        Map<String, Object> libera = byName(rooms, "Aula Libera");

        assertThat(libera.get("status")).isEqualTo("libera");
        assertThat(libera.get("booking")).isNull();
        assertThat(libera.get("blocked")).isNull();
    }

    @Test
    void ilDettaglioIncludeLElencoDellePrenotazioni() throws Exception {
        List<Map<String, Object>> rooms = roomsOf(get("/api/rooms/detailed"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> bookings =
                (List<Map<String, Object>>) byName(rooms, "Aula Occupata").get("bookings");

        assertThat(bookings).hasSize(1);
        assertThat(bookings.get(0).keySet())
                .containsExactlyInAnyOrder("date", "startTime", "endTime", "user", "purpose");
        assertThat(bookings.get(0).get("user")).isEqualTo("Mario Rossi");
    }

    // ==================== clone 2: /api/rooms/{id}/detailed ====================

    @Test
    void ilDettaglioDiUnaSolaAulaRiportaLaPrenotazioneInCorso() throws Exception {
        ResponseEntity<String> resp = get("/api/rooms/" + aulaOccupataId + "/detailed");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) TestJson.comeMappa(resp.getBody()).get("data");
        @SuppressWarnings("unchecked")
        Map<String, Object> room = (Map<String, Object>) data.get("room");

        assertThat(room.get("status")).isEqualTo("prenotata");
        assertThat(room.get("booking")).isNotNull();
    }

    @Test
    void ilDettaglioDiUnaSolaAulaRiportaIDatiDelBlocco() throws Exception {
        ResponseEntity<String> resp = get("/api/rooms/" + aulaBloccataId + "/detailed");

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) TestJson.comeMappa(resp.getBody()).get("data");
        @SuppressWarnings("unchecked")
        Map<String, Object> room = (Map<String, Object>) data.get("room");

        assertThat(room.get("status")).isEqualTo("bloccata");
        assertThat(room.get("blocked")).isNotNull();
    }

    @Test
    void ilDettaglioDiUnaSolaAulaSegnalaLaPrenotazioneImminente() throws Exception {
        ResponseEntity<String> resp = get("/api/rooms/" + aulaImminenteId + "/detailed");

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) TestJson.comeMappa(resp.getBody()).get("data");
        @SuppressWarnings("unchecked")
        Map<String, Object> room = (Map<String, Object>) data.get("room");

        assertThat(room.get("status")).isEqualTo("prenotata");
    }

    // ==================== clone 3: physical/virtual detailed ====================

    @Test
    void ilDettaglioFisicoPortaLoStatoSoloPerLeAuleFisiche() throws Exception {
        List<Map<String, Object>> rooms = roomsOf(get("/api/rooms/physical/detailed"));

        assertThat(rooms).hasSize(4); // le 4 aule fisiche, l'aula virtuale e' esclusa
        assertThat(byName(rooms, "Aula Occupata").get("status")).isEqualTo("prenotata");
        assertThat(byName(rooms, "Aula Bloccata").get("status")).isEqualTo("bloccata");
        assertThat(byName(rooms, "Aula Libera").get("status")).isEqualTo("libera");
    }

    @Test
    void ilDettaglioVirtualePortaLoStatoSoloPerLeAuleVirtuali() throws Exception {
        List<Map<String, Object>> rooms = roomsOf(get("/api/rooms/virtual/detailed"));

        assertThat(rooms).hasSize(1);
        assertThat(byName(rooms, "Aula Virtuale Imminente").get("status")).isEqualTo("prenotata");
    }

    // ==================== stato aula (PrenotazioneService.getStatoAula) ====================

    @Test
    void statoAulaReflectsTheActiveBookingKind() throws Exception {
        assertThat(statoDi(aulaOccupataId)).isEqualTo("PRENOTATA");
        assertThat(statoDi(aulaBloccataId)).isEqualTo("BLOCCATA");
        assertThat(statoDi(aulaManutenzioneId)).isEqualTo("MANUTENZIONE");
        assertThat(statoDi(aulaLiberaId)).isEqualTo("LIBERA");
        // la prenotazione imminente non e' ancora attiva: l'aula risulta libera adesso
        assertThat(statoDi(aulaImminenteId)).isEqualTo("LIBERA");
    }

    private String statoDi(Long roomId) throws Exception {
        ResponseEntity<String> resp = get("/api/bookings/room-status/" + roomId);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) TestJson.comeMappa(resp.getBody()).get("stato");
    }

}
