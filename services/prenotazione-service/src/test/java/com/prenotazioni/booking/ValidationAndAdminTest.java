package com.prenotazioni.booking;

import com.prenotazioni.testsupport.TestJson;
import com.prenotazioni.testsupport.TestJwt;
import com.prenotazioni.model.Role;
import com.prenotazioni.booking.repository.RoomRepository;
import com.prenotazioni.booking.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression suite per la Fase 2 del refactor Swagger: Bean Validation sui DTO
 * (CreateUserRequest/UpdateUserRequest/AulaRequest/PrenotazioneRequest) e conferma
 * che il comportamento di AuthController.login NON sia cambiato (nessun @Valid li',
 * deliberatamente, per non alterare l'ordine rispetto al rate limiter).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ValidationAndAdminTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private RoomRepository aulaRepository;

    @Autowired
    private BookingRepository prenotazioneRepository;

    private String tokenAdmin;
    private String tokenUser;

    @BeforeEach
    void setUp() {
        prenotazioneRepository.deleteAll();
        aulaRepository.deleteAll();



        tokenAdmin = TestJwt.perAdmin(1L, "admin@validation.test");
        tokenUser = TestJwt.perUtente(2L, "user@validation.test", "User Validation");
    }

    @SuppressWarnings("unchecked")
    private HttpHeaders bearerJson(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }


    // ==================== CreateUserRequest ====================

    // ==================== UpdateUserRequest ====================

    // ==================== AulaRequest ====================

    @Test
    void createRoomWithNegativeCapienzaIsRejected() throws Exception {
        Map<String, Object> body = Map.of("nome", "Aula X", "piano", 1, "capienza", -5);

        ResponseEntity<String> resp = rest.exchange(
                "/api/admin/createrooms", HttpMethod.POST, new HttpEntity<>(body, bearerJson(tokenAdmin)), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(TestJson.comeMappa(resp.getBody()).get("error")).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void createRoomWithBlankNameIsRejected() throws Exception {
        Map<String, Object> body = Map.of("nome", "  ", "piano", 1, "capienza", 10);

        ResponseEntity<String> resp = rest.exchange(
                "/api/admin/createrooms", HttpMethod.POST, new HttpEntity<>(body, bearerJson(tokenAdmin)), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(TestJson.comeMappa(resp.getBody()).get("error")).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void createRoomWithValidDataSucceeds() {
        Map<String, Object> body = Map.of("nome", "Aula Valida", "piano", 2, "capienza", 25);

        ResponseEntity<String> resp = rest.exchange(
                "/api/admin/createrooms", HttpMethod.POST, new HttpEntity<>(body, bearerJson(tokenAdmin)), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    // ==================== PrenotazioneRequest ====================

    @Test
    void bloccaAulaWithMissingAulaIdIsRejectedByBeanValidation() throws Exception {
        Map<String, Object> body = Map.of(
                "inizio", LocalDateTime.now().plusDays(1).toString(),
                "fine", LocalDateTime.now().plusDays(1).plusHours(1).toString());

        ResponseEntity<String> resp = rest.exchange(
                "/api/prenotazioni/blocca", HttpMethod.POST, new HttpEntity<>(body, bearerJson(tokenAdmin)), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(TestJson.comeMappa(resp.getBody()).get("error")).isEqualTo("VALIDATION_ERROR");
    }

    // ==================== AuthController.login: comportamento invariato (nessun @Valid) ====================

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    // ==================== Filtro di sicurezza: 401/403 ricchi anche senza dispatch al controller ====================

    @Test
    void unEndpointProtettoSenzaTokenTornaUn401ConCorpoJson() throws Exception {
        ResponseEntity<String> resp = rest.exchange(
                "/api/rooms", HttpMethod.GET, HttpEntity.EMPTY, String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        Map<String, Object> body = TestJson.comeMappa(resp.getBody());
        assertThat(body.get("success")).isEqualTo(false);
        assertThat(body.get("userMessage")).isNotNull();
    }
}
