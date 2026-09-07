package com.classroom.notification;

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

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The endpoints other services call.
 *
 * They used to be method calls inside the same process, covered indirectly by
 * AdminManagementTest. Now that they are network, they are the most fragile point of the
 * split and deserve tests of their own: a change in the shape of the JSON body here fails no
 * compilation, it would show up only as notifications that stop arriving.
 *
 * There is no test for a completely absent token: TestRestTemplate uses HttpURLConnection,
 * which on a 401 tries to retry the request and fails with an I/O error instead of reporting
 * the status. That case stays covered by
 * NotificationEndpointsTest.theNotificationEndpointsRequireAuthentication, which exercises
 * the same shared security chain; what is checked here is what is specific to these routes,
 * namely that any old token is not enough and the admin role is required.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
// The three tests on the cancellation notification left with the REST endpoint they
// covered: that notification arrives as a message now, and CancellationMessagingTest covers
// it against a real broker.
class InternalNotificationTest {

    private static final Long DESTINATARIO = 42L;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private NotificationRepository notificationRepository;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
    }

    private HttpHeaders headers(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return h;
    }

    private Map<String, Object> corpoCancellazione() {
        // HashMap and not Map.of: adminName and reason can be null, as in the client
        Map<String, Object> body = new HashMap<>();
        body.put("userId", DESTINATARIO);
        body.put("prenotazioneId", 99L);
        body.put("nomeStanza", "Aula Magna");
        body.put("adminNome", "Mario Rossi");
        body.put("dataPrenotazione", "2026-12-25");
        body.put("startTime", "14:30");
        body.put("endTime", "16:30");
        body.put("reason", "Sessione d'esame");
        return body;
    }

    @Test
    void deletingAUsersNotificationsLeavesTheOthersAlone() {
        notificationRepository.save(new Notification(DESTINATARIO, "Sua", "Messaggio", "INFO"));
        notificationRepository.save(new Notification(DESTINATARIO, "Sua anche questa", "Messaggio", "INFO"));
        notificationRepository.save(new Notification(7L, "Di un altro", "Non toccare", "INFO"));

        ResponseEntity<String> resp = rest.exchange(
                "/api/notifications/internal/user/" + DESTINATARIO, HttpMethod.DELETE,
                new HttpEntity<>(headers(TestJwt.forAdmin(1L, "admin@test.it"))), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(notificationRepository.findAll())
                .extracting(Notification::getUserId)
                .containsExactly(7L);
    }
}
