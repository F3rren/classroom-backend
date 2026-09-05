package com.prenotazioni.notification;

import com.prenotazioni.testsupport.TestJson;
import com.prenotazioni.notification.model.Notification;
import com.prenotazioni.notification.repository.NotificationRepository;
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
class NotificationOwnershipTest {

    private static final Long OWNER_ID = 10L;
    private static final Long OTHER_ID = 20L;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private NotificationRepository notificationRepository;

    private String tokenOwner;
    private String tokenOther;
    private Long notificaIdDiOwner;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();

        Notification notification = new Notification(OWNER_ID, "Titolo", "Messaggio di test", "INFO");
        notificationRepository.save(notification);
        notificaIdDiOwner = notification.getId();

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
        ResponseEntity<Notification[]> resp = rest.exchange(
                "/api/notifications", HttpMethod.GET, new HttpEntity<>(bearer(tokenOwner)), Notification[].class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).hasSize(1);
        assertThat(resp.getBody()[0].getId()).isEqualTo(notificaIdDiOwner);
    }

    @Test
    void otherUserDoesNotSeeOwnersNotifications() {
        ResponseEntity<Notification[]> resp = rest.exchange(
                "/api/notifications", HttpMethod.GET, new HttpEntity<>(bearer(tokenOther)), Notification[].class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isEmpty();
    }

    @Test
    void otherUserCannotDeleteOwnersNotification() {
        ResponseEntity<String> resp = rest.exchange(
                "/api/notifications/" + notificaIdDiOwner, HttpMethod.DELETE,
                new HttpEntity<>(bearer(tokenOther)), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(notificationRepository.existsById(notificaIdDiOwner)).isTrue();
    }

    @Test
    void ownerCanMarkTheirOwnNotificationAsRead() {
        ResponseEntity<Notification> resp = rest.exchange(
                "/api/notifications/" + notificaIdDiOwner + "/mark-read", HttpMethod.PUT,
                new HttpEntity<>(bearer(tokenOwner)), Notification.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().getRead()).isTrue();
    }

    @Test
    void countNonLetteReflectsUnreadNotifications() throws Exception {
        ResponseEntity<String> resp = rest.exchange(
                "/api/notifications/unread-count", HttpMethod.GET,
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
                "/api/notifications", HttpMethod.GET, new HttpEntity<>(bearer(tokenFasullo)), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
