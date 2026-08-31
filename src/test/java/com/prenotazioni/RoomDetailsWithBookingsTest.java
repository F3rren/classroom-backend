package com.prenotazioni;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prenotazioni.model.Aula;
import com.prenotazioni.model.Prenotazione;
import com.prenotazioni.model.StatoPrenotazione;
import com.prenotazioni.model.Utente;
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
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RoomDetailsWithBookingsTest {

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
    private Long aulaOccupataId;
    private Long aulaBloccataId;
    private Long aulaManutenzioneId;
    private Long aulaImminenteId;
    private Long aulaLiberaId;

    @BeforeEach
    void setUp() {
        prenotazioneRepository.deleteAll();
        aulaRepository.deleteAll();
        utenteRepository.deleteAll();

        Utente user = new Utente();
        user.setEmail("dettagli@test.it");
        user.setUsername("dettagli");
        user.setPassword(passwordEncoder.encode("dettagli-password"));
        user.setNome("Mario Rossi"); // il nome finisce dentro currentBooking: non puo' essere null
        user.setRuolo("user");
        user.setDataRegistrazione(LocalDateTime.now());
        utenteRepository.save(user);

        LocalDateTime ora = LocalDateTime.now();

        aulaOccupataId = salvaAula("Aula Occupata", 1, 30, false);
        prenota(aulaOccupataId, user, StatoPrenotazione.PRENOTATA, ora.minusHours(1), ora.plusHours(1), "Lezione di Analisi");

        aulaBloccataId = salvaAula("Aula Bloccata", 1, 30, false);
        prenota(aulaBloccataId, user, StatoPrenotazione.BLOCCATA, ora.minusHours(1), ora.plusHours(1), "Evento riservato");

        aulaManutenzioneId = salvaAula("Aula Manutenzione", 2, 20, false);
        prenota(aulaManutenzioneId, user, StatoPrenotazione.MANUTENZIONE, ora.minusHours(1), ora.plusHours(1), "Sostituzione proiettore");

        // Aula virtuale con prenotazione IMMINENTE (entro 2 ore, ma non ancora iniziata):
        // copre il secondo ramo e il lato virtuale del terzo clone.
        aulaImminenteId = salvaAula("Aula Virtuale Imminente", 0, 50, true);
        prenota(aulaImminenteId, user, StatoPrenotazione.PRENOTATA, ora.plusMinutes(30), ora.plusMinutes(90), null);

        aulaLiberaId = salvaAula("Aula Libera", 3, 10, false);

        token = login("dettagli@test.it", "dettagli-password");
    }

    private Long salvaAula(String nome, int piano, int capienza, boolean virtuale) {
        Aula a = new Aula();
        a.setNome(nome);
        a.setPiano(piano);
        a.setCapienza(capienza);
        a.setVirtual(virtuale);
        a.setStato("libera");
        return aulaRepository.save(a).getId();
    }

    private void prenota(Long aulaId, Utente utente, StatoPrenotazione stato,
                         LocalDateTime inizio, LocalDateTime fine, String descrizione) {
        Prenotazione p = new Prenotazione();
        p.setAula(aulaRepository.findById(aulaId).orElseThrow());
        p.setUtente(utente);
        p.setInizio(inizio);
        p.setFine(fine);
        p.setStato(stato);
        p.setDescrizione(descrizione);
        p.setDataCreazione(LocalDateTime.now()); // letto da blockInfo.blockedAt
        prenotazioneRepository.save(p);
    }

    @SuppressWarnings("unchecked")
    private String login(String email, String password) {
        ResponseEntity<Map> resp = rest.postForEntity(
                "/api/auth/login", Map.of("email", email, "password", password), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) resp.getBody().get("token");
    }

    private ResponseEntity<String> get(String url) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return rest.exchange(url, HttpMethod.GET, new HttpEntity<>(h), String.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(String json) throws Exception {
        return objectMapper.readValue(json, Map.class);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> roomsOf(ResponseEntity<String> resp) throws Exception {
        Map<String, Object> data = (Map<String, Object>) asMap(resp.getBody()).get("data");
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
    void detailedMarksCurrentlyBookedRoomWithCurrentBooking() throws Exception {
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
    void detailedMarksBlockedRoomWithBlockInfo() throws Exception {
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
    void detailedTreatsMaintenanceAsBlocked() throws Exception {
        List<Map<String, Object>> rooms = roomsOf(get("/api/rooms/detailed"));
        Map<String, Object> manutenzione = byName(rooms, "Aula Manutenzione");

        assertThat(manutenzione.get("status")).isEqualTo("bloccata");
        assertThat(manutenzione.get("blocked")).isNotNull();
    }

    @Test
    void detailedFlagsBookingStartingWithinTwoHours() throws Exception {
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
    void detailedLeavesRoomWithoutBookingsFree() throws Exception {
        List<Map<String, Object>> rooms = roomsOf(get("/api/rooms/detailed"));
        Map<String, Object> libera = byName(rooms, "Aula Libera");

        assertThat(libera.get("status")).isEqualTo("libera");
        assertThat(libera.get("booking")).isNull();
        assertThat(libera.get("blocked")).isNull();
    }

    @Test
    void detailedIncludesBookingList() throws Exception {
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
    void singleRoomDetailedReportsCurrentBooking() throws Exception {
        ResponseEntity<String> resp = get("/api/rooms/" + aulaOccupataId + "/detailed");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) asMap(resp.getBody()).get("data");
        @SuppressWarnings("unchecked")
        Map<String, Object> room = (Map<String, Object>) data.get("room");

        assertThat(room.get("status")).isEqualTo("prenotata");
        assertThat(room.get("booking")).isNotNull();
    }

    @Test
    void singleRoomDetailedReportsBlockInfo() throws Exception {
        ResponseEntity<String> resp = get("/api/rooms/" + aulaBloccataId + "/detailed");

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) asMap(resp.getBody()).get("data");
        @SuppressWarnings("unchecked")
        Map<String, Object> room = (Map<String, Object>) data.get("room");

        assertThat(room.get("status")).isEqualTo("bloccata");
        assertThat(room.get("blocked")).isNotNull();
    }

    @Test
    void singleRoomDetailedFlagsImminentBooking() throws Exception {
        ResponseEntity<String> resp = get("/api/rooms/" + aulaImminenteId + "/detailed");

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) asMap(resp.getBody()).get("data");
        @SuppressWarnings("unchecked")
        Map<String, Object> room = (Map<String, Object>) data.get("room");

        assertThat(room.get("status")).isEqualTo("prenotata");
    }

    // ==================== clone 3: physical/virtual detailed ====================

    @Test
    void physicalDetailedCarriesBookingStateForPhysicalRoomsOnly() throws Exception {
        List<Map<String, Object>> rooms = roomsOf(get("/api/rooms/physical/detailed"));

        assertThat(rooms).hasSize(4); // le 4 aule fisiche, l'aula virtuale e' esclusa
        assertThat(byName(rooms, "Aula Occupata").get("status")).isEqualTo("prenotata");
        assertThat(byName(rooms, "Aula Bloccata").get("status")).isEqualTo("bloccata");
        assertThat(byName(rooms, "Aula Libera").get("status")).isEqualTo("libera");
    }

    @Test
    void virtualDetailedCarriesBookingStateForVirtualRoomsOnly() throws Exception {
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

    private String statoDi(Long aulaId) throws Exception {
        ResponseEntity<String> resp = get("/api/prenotazioni/stato-aula/" + aulaId);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) asMap(resp.getBody()).get("stato");
    }
}
