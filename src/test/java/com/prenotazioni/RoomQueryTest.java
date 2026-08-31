package com.prenotazioni;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prenotazioni.model.Aula;
import com.prenotazioni.model.StatoAula;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Copre gli endpoint di lettura di /api/rooms finora senza test: /capienza, /detailed,
 * /details, /stats, /virtual, /virtual/detailed, /physical/detailed e /{id}/detailed.
 *
 * Il dataset ha volutamente 2 aule fisiche e 1 virtuale con capienze diverse, cosi' i
 * filtri e le statistiche producono numeri distinguibili invece che tutti uguali a zero.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RoomQueryTest {

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
    private Long aulaFisicaGrandeId;

    @BeforeEach
    void setUp() {
        prenotazioneRepository.deleteAll();
        aulaRepository.deleteAll();
        utenteRepository.deleteAll();

        Utente user = new Utente();
        user.setEmail("roomquery@test.it");
        user.setUsername("roomquery");
        user.setPassword(passwordEncoder.encode("roomquery-password"));
        user.setNome("Room Query");
        user.setRuolo(Ruolo.USER);
        user.setDataRegistrazione(LocalDateTime.now());
        utenteRepository.save(user);

        aulaFisicaGrandeId = salvaAula("Aula Grande", 1, 100, false);
        salvaAula("Aula Piccola", 2, 10, false);
        salvaAula("Aula Virtuale", 0, 50, true);

        token = login("roomquery@test.it", "roomquery-password");
    }

    private Long salvaAula(String nome, int piano, int capienza, boolean virtuale) {
        Aula a = new Aula();
        a.setNome(nome);
        a.setPiano(piano);
        a.setCapienza(capienza);
        a.setVirtual(virtuale);
        a.setStato(StatoAula.LIBERA);
        return aulaRepository.save(a).getId();
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
    private Map<String, Object> dataOf(ResponseEntity<String> resp) throws Exception {
        return (Map<String, Object>) asMap(resp.getBody()).get("data");
    }

    @Test
    void capienzaFiltersRoomsAboveThreshold() throws Exception {
        ResponseEntity<String> resp = get("/api/rooms/capienza?minCapienza=50");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> data = dataOf(resp);
        // Aula Grande (100) e Aula Virtuale (50) passano, Aula Piccola (10) no
        assertThat(data.get("totalRooms")).isEqualTo(2);
        assertThat(data).containsEntry("capienzaMinima", 50);
        assertThat(data).containsEntry("maxCapacityFound", 100);
    }

    @Test
    void capienzaWithNoMatchReturnsSuggestion() throws Exception {
        ResponseEntity<String> resp = get("/api/rooms/capienza?minCapienza=999");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> data = dataOf(resp);
        assertThat(data.get("totalRooms")).isEqualTo(0);
        assertThat(data).containsKey("suggestion");
        assertThat(data).doesNotContainKey("maxCapacityFound");
    }

    @Test
    void capienzaAboveHardLimitIsRejected() throws Exception {
        ResponseEntity<String> resp = get("/api/rooms/capienza?minCapienza=1001");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(asMap(resp.getBody()).get("error")).isEqualTo("CAPACITY_TOO_HIGH");
    }

    @Test
    void negativeCapienzaIsRejected() throws Exception {
        ResponseEntity<String> resp = get("/api/rooms/capienza?minCapienza=-1");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(asMap(resp.getBody()).get("error")).isEqualTo("INVALID_CAPACITY");
    }

    @Test
    void virtualRoomsAreTaggedAndSeparatedFromPhysical() throws Exception {
        Map<String, Object> virtuali = dataOf(get("/api/rooms/virtual"));
        assertThat(virtuali).containsEntry("type", "virtual");
        assertThat(virtuali.get("totalRooms")).isEqualTo(1);

        Map<String, Object> fisiche = dataOf(get("/api/rooms/physical"));
        assertThat(fisiche).containsEntry("type", "physical");
        assertThat(fisiche.get("totalRooms")).isEqualTo(2);
    }

    @Test
    void detailedVariantsReturnSameCountsAsPlainVariants() throws Exception {
        assertThat(dataOf(get("/api/rooms/detailed")).get("totalRooms")).isEqualTo(3);
        assertThat(dataOf(get("/api/rooms/physical/detailed")).get("totalRooms")).isEqualTo(2);
        assertThat(dataOf(get("/api/rooms/virtual/detailed")).get("totalRooms")).isEqualTo(1);
    }

    @Test
    void physicalDetailedIsTaggedPhysical() throws Exception {
        assertThat(dataOf(get("/api/rooms/physical/detailed"))).containsEntry("type", "physical");
        assertThat(dataOf(get("/api/rooms/virtual/detailed"))).containsEntry("type", "virtual");
    }

    @Test
    void roomStatsSplitPhysicalAndVirtual() throws Exception {
        ResponseEntity<String> resp = get("/api/rooms/stats");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> stats = (Map<String, Object>) dataOf(resp).get("statistics");
        assertThat(stats.keySet()).containsExactlyInAnyOrder(
                "totalRooms", "physicalRooms", "virtualRooms",
                "physicalPercentage", "virtualPercentage", "hasRooms");
        assertThat(stats.get("totalRooms")).isEqualTo(3);
        assertThat(stats.get("physicalRooms")).isEqualTo(2);
        assertThat(stats.get("virtualRooms")).isEqualTo(1);
        assertThat(stats.get("hasRooms")).isEqualTo(true);
    }

    @Test
    void roomDetailedByIdIsWrappedInRoomKey() throws Exception {
        ResponseEntity<String> resp = get("/api/rooms/" + aulaFisicaGrandeId + "/detailed");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> data = dataOf(resp);
        assertThat(data.keySet()).containsExactly("room");
    }

    @Test
    void roomDetailedByIdNotFoundReturns404() throws Exception {
        ResponseEntity<String> resp = get("/api/rooms/999999/detailed");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(asMap(resp.getBody()).get("error")).isEqualTo("ROOM_NOT_FOUND");
    }

    @Test
    void allRoomsDetailsIsNotWrappedInEnvelope() throws Exception {
        ResponseEntity<String> resp = get("/api/rooms/details");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = asMap(resp.getBody());
        // shape storica: nessun envelope success/data
        assertThat(body.keySet()).containsExactlyInAnyOrder("prenotazioni", "totalPrenotazioni");
    }

    @Test
    void roomQueryEndpointsRequireAuthentication() {
        for (String url : new String[]{
                "/api/rooms/stats", "/api/rooms/virtual", "/api/rooms/detailed",
                "/api/rooms/capienza?minCapienza=1"}) {
            ResponseEntity<String> resp = rest.exchange(url, HttpMethod.GET, HttpEntity.EMPTY, String.class);
            assertThat(resp.getStatusCode())
                    .as("endpoint %s senza token", url)
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }
}
