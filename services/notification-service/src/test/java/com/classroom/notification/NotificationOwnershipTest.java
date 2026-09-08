package com.classroom.notification;

import com.classroom.testsupport.TestJson;
import com.classroom.notification.model.Notification;
import com.classroom.notification.repository.NotificationRepository;
import com.classroom.testsupport.TestJwt;
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
import org.springframework.lang.NonNull;
import org.springframework.test.context.ActiveProfiles;

import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Who sees a notification, and who may change it.
 *
 * These five cases lived in MeAndNotificationControllerTest back in the monolith, together
 * with two tests on /api/me. They shared a file because they shared the user fixture and the
 * login; with the domains split, that reason is gone and two distinct things remain: the
 * profile belongs to the user domain, notification isolation to this service.
 *
 * The tokens are signed by TestJwt: there is no users table here, and none is needed.
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
    private Long ownerNotificationId;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();

        Notification notification = new Notification(OWNER_ID, "Titolo", "Messaggio di test", "INFO");
        notificationRepository.save(notification);
        ownerNotificationId = notification.getId();

        tokenOwner = TestJwt.forUser(OWNER_ID, "me-owner@test.it");
        tokenOther = TestJwt.forUser(OTHER_ID, "me-other@test.it");
    }

    @NonNull
    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(Objects.requireNonNull(token));
        return headers;
    }


    @Test
    void ownerSeesTheirOwnNotifications() {
        ResponseEntity<Notification[]> resp = rest.exchange(
                "/api/notifications", HttpMethod.GET, new HttpEntity<>(bearer(tokenOwner)), Notification[].class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).hasSize(1);
        assertThat(resp.getBody()[0].getId()).isEqualTo(ownerNotificationId);
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
                "/api/notifications/" + ownerNotificationId, HttpMethod.DELETE,
                new HttpEntity<>(bearer(tokenOther)), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(notificationRepository.existsById(ownerNotificationId)).isTrue();
    }

    @Test
    void ownerCanMarkTheirOwnNotificationAsRead() {
        ResponseEntity<Notification> resp = rest.exchange(
                "/api/notifications/" + ownerNotificationId + "/mark-read", HttpMethod.PUT,
                new HttpEntity<>(bearer(tokenOwner)), Notification.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(Objects.requireNonNull(resp.getBody()).getRead()).isTrue();
    }

    @Test
    void theUnreadCountReflectsTheUnreadNotifications() throws Exception {
        ResponseEntity<String> resp = rest.exchange(
                "/api/notifications/unread-count", HttpMethod.GET,
                new HttpEntity<>(bearer(tokenOwner)), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(TestJson.asMap(resp.getBody()).get("count")).isEqualTo(1);
    }

    @Test
    void aTokenSignedWithAnotherSecretIsRejected() {
        // A direct test of the mechanism the whole split rests on: this service accepts a
        // token only if the signature checks out against the shared secret, without asking
        // anybody. An otherwise well-formed token signed elsewhere does not get through.
        String bogusToken = tokenOwner.substring(0, tokenOwner.lastIndexOf('.')) + ".firmaSbagliata";

        ResponseEntity<String> resp = rest.exchange(
                "/api/notifications", HttpMethod.GET, new HttpEntity<>(bearer(bogusToken)), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
