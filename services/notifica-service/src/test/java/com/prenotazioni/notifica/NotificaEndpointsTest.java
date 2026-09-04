package com.prenotazioni.notifica;

import com.prenotazioni.testsupport.TestJson;
import com.prenotazioni.notifica.model.Notifica;
import com.prenotazioni.notifica.repository.NotificaRepository;
import com.prenotazioni.testsupport.TestJwt;
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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Copre gli endpoint notifiche finora senza test: /non-lette, /mark-all-read e
 * DELETE /read.
 *
 * Ogni test verifica anche l'isolamento fra utenti: le operazioni "di massa"
 * (segna tutte come lette, elimina le lette) non devono toccare le notifiche altrui.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class NotificaEndpointsTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private NotificaRepository notificaRepository;

    // Identificativi arbitrari: questo servizio non possiede la tabella utenti e non
    // verifica che esistano. E' proprio il punto della separazione.
    private static final Long OWNER_ID = 1L;
    private static final Long OTHER_ID = 2L;

    private String tokenOwner;
    private String tokenOther;
    private Long notificaLettaId;

    @BeforeEach
    void setUp() {
        notificaRepository.deleteAll();

        // owner: 2 non lette + 1 gia' letta
        notificaRepository.save(new Notifica(OWNER_ID, "Prima", "Messaggio 1", "INFO"));
        notificaRepository.save(new Notifica(OWNER_ID, "Seconda", "Messaggio 2", "INFO"));
        Notifica letta = new Notifica(OWNER_ID, "Terza", "Gia' letta", "INFO");
        letta.setLetta(true);
        notificaLettaId = notificaRepository.save(letta).getId();

        // other: 1 non letta, che non deve mai essere toccata dalle operazioni di owner
        notificaRepository.save(new Notifica(OTHER_ID, "Altrui", "Non toccare", "INFO"));

        // Token firmati direttamente: niente utente da creare, niente login da chiamare.
        tokenOwner = TestJwt.perUtente(OWNER_ID, "notif-owner@test.it");
        tokenOther = TestJwt.perUtente(OTHER_ID, "notif-other@test.it");
    }

    private ResponseEntity<String> exchange(String url, HttpMethod method, String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return rest.exchange(url, method, new HttpEntity<>(h), String.class);
    }


    private long contaNonLette(Long utenteId) {
        return notificaRepository.findAll().stream()
                .filter(n -> n.getUtenteId().equals(utenteId))
                .filter(n -> !Boolean.TRUE.equals(n.getLetta()))
                .count();
    }

    @Test
    void nonLetteReturnsOnlyUnreadOfCaller() {
        ResponseEntity<Notifica[]> resp = rest.exchange(
                "/api/notifiche/non-lette", HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(tokenOwner)), Notifica[].class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        // 2 non lette di owner: quella gia' letta e quella di other sono escluse
        assertThat(resp.getBody()).hasSize(2);
    }

    @Test
    void markAllReadClearsOnlyCallersNotifications() throws Exception {
        ResponseEntity<String> resp = exchange("/api/notifiche/mark-all-read", HttpMethod.PUT, tokenOwner);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(TestJson.comeMappa(resp.getBody()).get("message"))
                .isEqualTo("Tutte le notifiche sono state segnate come lette");

        assertThat(contaNonLette(OWNER_ID)).isZero();
        // la notifica dell'altro utente resta non letta
        assertThat(contaNonLette(OTHER_ID)).isEqualTo(1);
    }

    @Test
    void countNonLetteIsZeroAfterMarkAllRead() throws Exception {
        exchange("/api/notifiche/mark-all-read", HttpMethod.PUT, tokenOwner);

        ResponseEntity<String> resp = exchange("/api/notifiche/count-non-lette", HttpMethod.GET, tokenOwner);
        assertThat(TestJson.comeMappa(resp.getBody()).get("count")).isEqualTo(0);
    }

    @Test
    void deleteReadRemovesOnlyReadNotificationsOfCaller() throws Exception {
        ResponseEntity<String> resp = exchange("/api/notifiche/read", HttpMethod.DELETE, tokenOwner);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(TestJson.comeMappa(resp.getBody()).get("message")).isEqualTo("Notifiche lette eliminate con successo");

        // sparisce solo quella gia' letta; le 2 non lette restano
        assertThat(notificaRepository.existsById(notificaLettaId)).isFalse();
        assertThat(contaNonLette(OWNER_ID)).isEqualTo(2);
    }

    @Test
    void deleteReadAfterMarkAllReadEmptiesOwnInbox() {
        exchange("/api/notifiche/mark-all-read", HttpMethod.PUT, tokenOwner);
        exchange("/api/notifiche/read", HttpMethod.DELETE, tokenOwner);

        long rimasteDiOwner = notificaRepository.findAll().stream()
                .filter(n -> n.getUtenteId().equals(OWNER_ID))
                .count();
        assertThat(rimasteDiOwner).isZero();

        // l'inbox dell'altro utente e' intatta
        long rimasteDiOther = notificaRepository.findAll().stream()
                .filter(n -> n.getUtenteId().equals(OTHER_ID))
                .count();
        assertThat(rimasteDiOther).isEqualTo(1);
    }

    @Test
    void otherUserCannotMarkOwnersNotificationAsRead() {
        Long idDiOwner = notificaRepository.findAll().stream()
                .filter(n -> n.getUtenteId().equals(OWNER_ID))
                .findFirst().orElseThrow().getId();

        ResponseEntity<String> resp = exchange(
                "/api/notifiche/" + idDiOwner + "/mark-read", HttpMethod.PUT, tokenOther);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void notificaEndpointsRequireAuthentication() {
        for (String url : new String[]{"/api/notifiche/non-lette", "/api/notifiche/count-non-lette"}) {
            ResponseEntity<String> resp = rest.exchange(url, HttpMethod.GET, HttpEntity.EMPTY, String.class);
            assertThat(resp.getStatusCode())
                    .as("endpoint %s senza token", url)
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    private HttpHeaders bearerHeaders(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return h;
    }
}
