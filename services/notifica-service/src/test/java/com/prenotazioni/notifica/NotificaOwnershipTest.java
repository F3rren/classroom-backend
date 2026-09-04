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
 * Chi vede e chi puo' modificare una notifica.
 *
 * Questi cinque casi stavano in MeAndNotificaControllerTest nel monolite, insieme a due
 * test su /api/me. Erano nello stesso file perche' condividevano la fixture di utenti e
 * il login; separati i domini, quella ragione e' scomparsa e restano due cose distinte:
 * il profilo appartiene al dominio utenti, l'isolamento delle notifiche a questo servizio.
 *
 * I token sono firmati da TestJwt: qui non esiste una tabella utenti, e non serve.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class NotificaOwnershipTest {

    private static final Long OWNER_ID = 10L;
    private static final Long OTHER_ID = 20L;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private NotificaRepository notificaRepository;

    private String tokenOwner;
    private String tokenOther;
    private Long notificaIdDiOwner;

    @BeforeEach
    void setUp() {
        notificaRepository.deleteAll();

        Notifica notifica = new Notifica(OWNER_ID, "Titolo", "Messaggio di test", "INFO");
        notificaRepository.save(notifica);
        notificaIdDiOwner = notifica.getId();

        tokenOwner = TestJwt.perUtente(OWNER_ID, "me-owner@test.it");
        tokenOther = TestJwt.perUtente(OTHER_ID, "me-other@test.it");
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }


    @Test
    void ownerSeesTheirOwnNotifications() {
        ResponseEntity<Notifica[]> resp = rest.exchange(
                "/api/notifiche", HttpMethod.GET, new HttpEntity<>(bearer(tokenOwner)), Notifica[].class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).hasSize(1);
        assertThat(resp.getBody()[0].getId()).isEqualTo(notificaIdDiOwner);
    }

    @Test
    void otherUserDoesNotSeeOwnersNotifications() {
        ResponseEntity<Notifica[]> resp = rest.exchange(
                "/api/notifiche", HttpMethod.GET, new HttpEntity<>(bearer(tokenOther)), Notifica[].class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isEmpty();
    }

    @Test
    void otherUserCannotDeleteOwnersNotification() {
        ResponseEntity<String> resp = rest.exchange(
                "/api/notifiche/" + notificaIdDiOwner, HttpMethod.DELETE,
                new HttpEntity<>(bearer(tokenOther)), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(notificaRepository.existsById(notificaIdDiOwner)).isTrue();
    }

    @Test
    void ownerCanMarkTheirOwnNotificationAsRead() {
        ResponseEntity<Notifica> resp = rest.exchange(
                "/api/notifiche/" + notificaIdDiOwner + "/mark-read", HttpMethod.PUT,
                new HttpEntity<>(bearer(tokenOwner)), Notifica.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().getLetta()).isTrue();
    }

    @Test
    void countNonLetteReflectsUnreadNotifications() throws Exception {
        ResponseEntity<String> resp = rest.exchange(
                "/api/notifiche/count-non-lette", HttpMethod.GET,
                new HttpEntity<>(bearer(tokenOwner)), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(TestJson.comeMappa(resp.getBody()).get("count")).isEqualTo(1);
    }

    @Test
    void aTokenSignedWithAnotherSecretIsRejected() {
        // Prova diretta del meccanismo su cui poggia l'intera separazione: questo servizio
        // accetta un token solo se la firma torna col segreto condiviso, senza consultare
        // nessuno. Un token altrimenti ben formato ma firmato altrove non passa.
        String tokenFasullo = tokenOwner.substring(0, tokenOwner.lastIndexOf('.')) + ".firmaSbagliata";

        ResponseEntity<String> resp = rest.exchange(
                "/api/notifiche", HttpMethod.GET, new HttpEntity<>(bearer(tokenFasullo)), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
