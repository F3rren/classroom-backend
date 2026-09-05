package com.prenotazioni.notification;

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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Gli endpoint che altri servizi chiamano.
 *
 * Prima erano invocazioni di metodo dentro lo stesso processo, coperte di riflesso dai test
 * di AdminManagementTest. Diventate rete, sono il punto piu' fragile della separazione e
 * meritano test propri: un cambio di forma del corpo JSON qui non fa fallire alcuna
 * compilazione, si manifesterebbe solo come notifiche che smettono di arrivare.
 *
 * Non c'e' un test sull'assenza totale di token: TestRestTemplate usa HttpURLConnection,
 * che di fronte a un 401 tenta di ritentare la richiesta e fallisce con un errore di I/O
 * invece di riportare lo stato. Il caso resta coperto da
 * NotificaEndpointsTest.notificaEndpointsRequireAuthentication, che esercita la stessa
 * catena di sicurezza condivisa; qui si verifica cio' che e' specifico di queste rotte,
 * cioe' che non basta un token qualunque ma serve il ruolo admin.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
// I tre test sulla notifica di cancellazione sono spariti da qui insieme all'endpoint
// REST che verificavano: quella notifica arriva ora come messaggio, ed e'
// MessaggisticaCancellazioniTest a coprirla, contro un broker vero.
class InternalNotificationTest {

    private static final Long DESTINATARIO = 42L;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private NotificationRepository notificaRepository;

    @BeforeEach
    void setUp() {
        notificaRepository.deleteAll();
    }

    private HttpHeaders headers(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return h;
    }

    private Map<String, Object> corpoCancellazione() {
        // HashMap e non Map.of: adminNome e motivo possono essere null, come nel client
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("utenteId", DESTINATARIO);
        corpo.put("prenotazioneId", 99L);
        corpo.put("nomeStanza", "Aula Magna");
        corpo.put("adminNome", "Mario Rossi");
        corpo.put("dataPrenotazione", "2026-12-25");
        corpo.put("oraInizio", "14:30");
        corpo.put("oraFine", "16:30");
        corpo.put("motivo", "Sessione d'esame");
        return corpo;
    }

    @Test
    void deletingAUsersNotificationsLeavesTheOthersAlone() {
        notificaRepository.save(new Notification(DESTINATARIO, "Sua", "Messaggio", "INFO"));
        notificaRepository.save(new Notification(DESTINATARIO, "Sua anche questa", "Messaggio", "INFO"));
        notificaRepository.save(new Notification(7L, "Di un altro", "Non toccare", "INFO"));

        ResponseEntity<String> resp = rest.exchange(
                "/api/notifiche/interne/utente/" + DESTINATARIO, HttpMethod.DELETE,
                new HttpEntity<>(headers(TestJwt.perAdmin(1L, "admin@test.it"))), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(notificaRepository.findAll())
                .extracting(Notification::getUtenteId)
                .containsExactly(7L);
    }
}
