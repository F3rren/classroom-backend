package com.prenotazioni.prenotazione;

import com.prenotazioni.testsupport.TestJson;
import com.prenotazioni.prenotazione.model.ProprietarioPrenotazione;
import com.prenotazioni.testsupport.TestJwt;
import com.prenotazioni.prenotazione.model.Aula;
import com.prenotazioni.prenotazione.model.StatoAula;
import com.prenotazioni.model.Ruolo;
import com.prenotazioni.prenotazione.repository.IAulaRepository;
import com.prenotazioni.prenotazione.repository.IPrenotazioneRepository;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression suite per la Fase 3 del refactor Swagger: RoomController senza piu'
 * @RequestHeader("Authorization")/checkAuth manuale, con risposte tipizzate (RoomListPayload ecc).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RoomControllerTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private IAulaRepository aulaRepository;

    @Autowired
    private IPrenotazioneRepository prenotazioneRepository;

    private String token;
    private Long aulaId;

    @BeforeEach
    void setUp() {
        // Le prenotazioni PRIMA delle aule: hanno una chiave esterna verso di esse, e
        // cancellare un'aula ancora referenziata viola il vincolo.
        //
        // Mancava, ed era l'unica classe delle sette sulle aule a non farlo. Non si vedeva
        // perche' @DirtiesContext ricostruiva il contesto - e con esso l'H2 in memoria -
        // dopo ogni classe: le righe della classe precedente non esistevano mai. Tolto
        // quello, il difetto e' venuto fuori subito, ed era li' da sempre.
        prenotazioneRepository.deleteAll();
        aulaRepository.deleteAll();

        ProprietarioPrenotazione user = new ProprietarioPrenotazione(1L, "room-user", "Room User");

        Aula aula = new Aula();
        aula.setNome("Aula Room Test");
        aula.setPiano(3);
        aula.setCapienza(15);
        aula.setVirtual(false);
        aula.setStato(StatoAula.LIBERA);
        aulaId = aulaRepository.save(aula).getId();

        token = TestJwt.perUtente(1L, "room-user@test.it", "Room User");
    }


    private HttpHeaders bearer() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }


    @Test
    void lElencoDelleAuleTornaUnaListaTipizzata() throws Exception {
        ResponseEntity<String> resp = rest.exchange(
                "/api/rooms", HttpMethod.GET, new HttpEntity<>(bearer()), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = TestJson.comeMappa(resp.getBody());
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        assertThat(data.keySet()).containsExactlyInAnyOrder("rooms", "totalRooms");
        assertThat((Integer) data.get("totalRooms")).isEqualTo(1);
    }

    @Test
    void lAulaPerIdPortaICampiDenormalizzati() throws Exception {
        ResponseEntity<String> resp = rest.exchange(
                "/api/rooms/" + aulaId, HttpMethod.GET, new HttpEntity<>(bearer()), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> data = (Map<String, Object>) TestJson.comeMappa(resp.getBody()).get("data");
        assertThat(data.keySet()).containsExactlyInAnyOrder("room", "roomId", "roomName", "floor", "capacity");
        assertThat(data.get("roomName")).isEqualTo("Aula Room Test");
        assertThat(data.get("floor")).isEqualTo(3);
    }

    @Test
    void unAulaInesistenteRisponde404() {
        ResponseEntity<String> resp = rest.exchange(
                "/api/rooms/999999", HttpMethod.GET, new HttpEntity<>(bearer()), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void ilDettaglioDiUnAulaNonEAvvoltoNellaBusta() throws Exception {
        ResponseEntity<String> resp = rest.exchange(
                "/api/rooms/" + aulaId + "/details", HttpMethod.GET, new HttpEntity<>(bearer()), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = TestJson.comeMappa(resp.getBody());
        // Nessun envelope success/message/data qui: shape gia' esistente, preservata
        assertThat(body.keySet()).containsExactlyInAnyOrder("aula", "prenotazioni", "totalPrenotazioni");
    }

    @Test
    void leAulePerPianoIncludonoIlCampoPiano() throws Exception {
        ResponseEntity<String> resp = rest.exchange(
                "/api/rooms/piano/3", HttpMethod.GET, new HttpEntity<>(bearer()), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> data = (Map<String, Object>) TestJson.comeMappa(resp.getBody()).get("data");
        assertThat(data).containsEntry("piano", 3);
        assertThat(data).doesNotContainKey("type"); // campo opzionale omesso quando non usato
    }

    @Test
    void leAuleFisicheIncludonoIlCampoTipo() throws Exception {
        ResponseEntity<String> resp = rest.exchange(
                "/api/rooms/physical", HttpMethod.GET, new HttpEntity<>(bearer()), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> data = (Map<String, Object>) TestJson.comeMappa(resp.getBody()).get("data");
        assertThat(data).containsEntry("type", "physical");
        assertThat(data).doesNotContainKey("piano");
    }

    @Test
    void gliEndpointDelleAuleSenzaTokenRispondono401() {
        ResponseEntity<String> resp = rest.exchange(
                "/api/rooms", HttpMethod.GET, HttpEntity.EMPTY, String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
