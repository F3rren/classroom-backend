package com.prenotazioni.prenotazione;

import com.prenotazioni.testsupport.TestJson;
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
 * Copre gli endpoint di lettura di /api/rooms finora senza test: /capienza, /detailed,
 * /details, /stats, /virtual, /virtual/detailed, /physical/detailed e /{id}/detailed.
 *
 * Il dataset ha volutamente 2 aule fisiche e 1 virtuale con capienze diverse, cosi' i
 * filtri e le statistiche producono numeri distinguibili invece che tutti uguali a zero.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RoomQueryTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private IAulaRepository aulaRepository;

    @Autowired
    private IPrenotazioneRepository prenotazioneRepository;

    private String token;
    private Long aulaFisicaGrandeId;

    @BeforeEach
    void setUp() {
        prenotazioneRepository.deleteAll();
        aulaRepository.deleteAll();


        aulaFisicaGrandeId = salvaAula("Aula Grande", 1, 100, false);
        salvaAula("Aula Piccola", 2, 10, false);
        salvaAula("Aula Virtuale", 0, 50, true);

        token = TestJwt.perUtente(1L, "roomquery@test.it", "Room Query");
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
    private ResponseEntity<String> get(String url) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return rest.exchange(url, HttpMethod.GET, new HttpEntity<>(h), String.class);
    }


    @SuppressWarnings("unchecked")
    private Map<String, Object> dataOf(ResponseEntity<String> resp) throws Exception {
        return (Map<String, Object>) TestJson.comeMappa(resp.getBody()).get("data");
    }

    @Test
    void laCapienzaFiltraLeAuleSopraLaSoglia() throws Exception {
        ResponseEntity<String> resp = get("/api/rooms/capienza?minCapienza=50");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> data = dataOf(resp);
        // Aula Grande (100) e Aula Virtuale (50) passano, Aula Piccola (10) no
        assertThat(data.get("totalRooms")).isEqualTo(2);
        assertThat(data).containsEntry("capienzaMinima", 50);
        assertThat(data).containsEntry("maxCapacityFound", 100);
    }

    @Test
    void laCapienzaSenzaRisultatiTornaUnSuggerimento() throws Exception {
        ResponseEntity<String> resp = get("/api/rooms/capienza?minCapienza=999");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> data = dataOf(resp);
        assertThat(data.get("totalRooms")).isEqualTo(0);
        assertThat(data).containsKey("suggestion");
        assertThat(data).doesNotContainKey("maxCapacityFound");
    }

    @Test
    void unaCapienzaOltreIlMassimoVieneRifiutata() throws Exception {
        ResponseEntity<String> resp = get("/api/rooms/capienza?minCapienza=1001");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(TestJson.comeMappa(resp.getBody()).get("error")).isEqualTo("CAPACITY_TOO_HIGH");
    }

    @Test
    void unaCapienzaNegativaVieneRifiutata() throws Exception {
        ResponseEntity<String> resp = get("/api/rooms/capienza?minCapienza=-1");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(TestJson.comeMappa(resp.getBody()).get("error")).isEqualTo("INVALID_CAPACITY");
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
    void leVariantiDettagliateTornanoGliStessiConteggi() throws Exception {
        assertThat(dataOf(get("/api/rooms/detailed")).get("totalRooms")).isEqualTo(3);
        assertThat(dataOf(get("/api/rooms/physical/detailed")).get("totalRooms")).isEqualTo(2);
        assertThat(dataOf(get("/api/rooms/virtual/detailed")).get("totalRooms")).isEqualTo(1);
    }

    @Test
    void ilDettaglioFisicoEEtichettatoComeFisico() throws Exception {
        assertThat(dataOf(get("/api/rooms/physical/detailed"))).containsEntry("type", "physical");
        assertThat(dataOf(get("/api/rooms/virtual/detailed"))).containsEntry("type", "virtual");
    }

    @Test
    void leStatisticheSeparanoAuleFisicheEVirtuali() throws Exception {
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
    void ilDettaglioPerIdEAvvoltoNellaChiaveRoom() throws Exception {
        ResponseEntity<String> resp = get("/api/rooms/" + aulaFisicaGrandeId + "/detailed");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> data = dataOf(resp);
        assertThat(data.keySet()).containsExactly("room");
    }

    @Test
    void ilDettaglioDiUnAulaInesistenteRisponde404() throws Exception {
        ResponseEntity<String> resp = get("/api/rooms/999999/detailed");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(TestJson.comeMappa(resp.getBody()).get("error")).isEqualTo("ROOM_NOT_FOUND");
    }

    @Test
    void ilDettaglioDiTutteLeAuleNonEAvvoltoNellaBusta() throws Exception {
        ResponseEntity<String> resp = get("/api/rooms/details");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = TestJson.comeMappa(resp.getBody());
        // shape storica: nessun envelope success/data
        assertThat(body.keySet()).containsExactlyInAnyOrder("prenotazioni", "totalPrenotazioni");
    }

    @Test
    void gliEndpointDiInterrogazioneAuleRichiedonoAutenticazione() {
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
