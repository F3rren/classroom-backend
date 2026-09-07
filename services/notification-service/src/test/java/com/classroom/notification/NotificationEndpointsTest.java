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
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the notification endpoints that had no test until now: /unread, /mark-all-read and
 * DELETE /read.
 *
 * Every test also checks isolation between users: the "bulk" operations (mark all as read,
 * delete the read ones) must never touch somebody else's notifications.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class NotificationEndpointsTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private NotificationRepository notificationRepository;

    // Arbitrary ids: this service does not own the users table and does not check that they
    // exist. That is precisely the point of the split.
    private static final Long OWNER_ID = 1L;
    private static final Long OTHER_ID = 2L;

    private String tokenOwner;
    private String tokenOther;
    private Long readNotificationId;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();

        // owner: 2 unread + 1 already read
        notificationRepository.save(new Notification(OWNER_ID, "Prima", "Messaggio 1", "INFO"));
        notificationRepository.save(new Notification(OWNER_ID, "Seconda", "Messaggio 2", "INFO"));
        Notification read = new Notification(OWNER_ID, "Terza", "Gia' letta", "INFO");
        read.setRead(true);
        readNotificationId = notificationRepository.save(read).getId();

        // other: 1 unread, which owner's operations must never touch
        notificationRepository.save(new Notification(OTHER_ID, "Altrui", "Non toccare", "INFO"));

        // Tokens signed directly: no user to create, no login to call.
        tokenOwner = TestJwt.forUser(OWNER_ID, "notif-owner@test.it");
        tokenOther = TestJwt.forUser(OTHER_ID, "notif-other@test.it");
    }

    private ResponseEntity<String> exchange(String url, HttpMethod method, String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return rest.exchange(url, method, new HttpEntity<>(h), String.class);
    }


    private long countUnread(Long userId) {
        return notificationRepository.findAll().stream()
                .filter(n -> n.getUserId().equals(userId))
                .filter(n -> !Boolean.TRUE.equals(n.getRead()))
                .count();
    }

    @Test
    void unreadReturnsOnlyTheCallersUnreadNotifications() {
        ResponseEntity<Notification[]> resp = rest.exchange(
                "/api/notifications/unread", HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(tokenOwner)), Notification[].class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        // owner's 2 unread: the already-read one and other's are excluded
        assertThat(resp.getBody()).hasSize(2);
    }

    @Test
    void markAllReadClearsOnlyCallersNotifications() throws Exception {
        ResponseEntity<String> resp = exchange("/api/notifications/mark-all-read", HttpMethod.PUT, tokenOwner);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(TestJson.asMap(resp.getBody()).get("message"))
                .isEqualTo("Tutte le notifiche sono state segnate come lette");

        assertThat(countUnread(OWNER_ID)).isZero();
        // the other user's notification stays unread
        assertThat(countUnread(OTHER_ID)).isEqualTo(1);
    }

    @Test
    void theUnreadCountIsZeroAfterMarkAllRead() throws Exception {
        exchange("/api/notifications/mark-all-read", HttpMethod.PUT, tokenOwner);

        ResponseEntity<String> resp = exchange("/api/notifications/unread-count", HttpMethod.GET, tokenOwner);
        assertThat(TestJson.asMap(resp.getBody()).get("count")).isEqualTo(0);
    }

    @Test
    void deleteReadRemovesOnlyReadNotificationsOfCaller() throws Exception {
        ResponseEntity<String> resp = exchange("/api/notifications/read", HttpMethod.DELETE, tokenOwner);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(TestJson.asMap(resp.getBody()).get("message")).isEqualTo("Notifiche lette eliminate con successo");

        // only the already-read one disappears; the 2 unread stay
        assertThat(notificationRepository.existsById(readNotificationId)).isFalse();
        assertThat(countUnread(OWNER_ID)).isEqualTo(2);
    }

    @Test
    void deleteReadAfterMarkAllReadEmptiesOwnInbox() {
        exchange("/api/notifications/mark-all-read", HttpMethod.PUT, tokenOwner);
        exchange("/api/notifications/read", HttpMethod.DELETE, tokenOwner);

        long rimasteDiOwner = notificationRepository.findAll().stream()
                .filter(n -> n.getUserId().equals(OWNER_ID))
                .count();
        assertThat(rimasteDiOwner).isZero();

        // the other user's inbox is untouched
        long rimasteDiOther = notificationRepository.findAll().stream()
                .filter(n -> n.getUserId().equals(OTHER_ID))
                .count();
        assertThat(rimasteDiOther).isEqualTo(1);
    }

    @Test
    void otherUserCannotMarkOwnersNotificationAsRead() {
        Long idDiOwner = notificationRepository.findAll().stream()
                .filter(n -> n.getUserId().equals(OWNER_ID))
                .findFirst().orElseThrow().getId();

        ResponseEntity<String> resp = exchange(
                "/api/notifications/" + idDiOwner + "/mark-read", HttpMethod.PUT, tokenOther);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void theNotificationEndpointsRequireAuthentication() {
        for (String url : new String[]{"/api/notifications/unread", "/api/notifications/unread-count"}) {
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
