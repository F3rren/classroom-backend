package com.classroom.booking;

import com.classroom.testsupport.TestJson;
import com.classroom.testsupport.TestJwt;
import com.classroom.testsupport.TestQuery;
import com.classroom.booking.repository.RoomRepository;
import com.classroom.booking.repository.BookingRepository;
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

import java.net.URI;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The regression suite for Bean Validation on the DTOs
 * (CreateUserRequest/UpdateUserRequest/RoomRequest/BookingRequest).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ValidationAndAdminTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private BookingRepository bookingRepository;

    private String tokenAdmin;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        roomRepository.deleteAll();



        tokenAdmin = TestJwt.forAdmin(1L, "admin@validation.test");
    }

    /**
     * createRoom() and blockRoom() moved their request data from a JSON @RequestBody to
     * @ModelAttribute / query parameters, so Swagger UI can offer real fillable inputs
     * instead of a JSON box. This sends the same shape a browser or curl would - a query
     * string - instead of a body the controller no longer reads.
     *
     * URI.create, not the String overload the rest of this file still uses for /api/rooms:
     * TestRestTemplate's String-based exchange() would re-encode the pre-encoded query
     * string TestQuery already produced, turning "%40" into "%2540".
     */
    private ResponseEntity<String> postQuery(String url, String token, Map<String, ?> params) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(Objects.requireNonNull(token));
        return rest.exchange(URI.create(rest.getRootUri() + url + TestQuery.of(params)),
                HttpMethod.POST, new HttpEntity<>(headers), String.class);
    }


    // ==================== CreateUserRequest ====================

    // ==================== UpdateUserRequest ====================

    // ==================== AulaRequest ====================

    @Test
    void createRoomWithANegativeCapacityIsRejected() throws Exception {
        Map<String, Object> params = Map.of("name", "Aula X", "floor", 1, "capacity", -5);

        ResponseEntity<String> resp = postQuery("/api/admin/rooms", tokenAdmin, params);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(TestJson.asMap(resp.getBody()).get("error")).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void createRoomWithBlankNameIsRejected() throws Exception {
        Map<String, Object> params = Map.of("name", "  ", "floor", 1, "capacity", 10);

        ResponseEntity<String> resp = postQuery("/api/admin/rooms", tokenAdmin, params);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(TestJson.asMap(resp.getBody()).get("error")).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void createRoomWithValidDataSucceeds() {
        Map<String, Object> params = Map.of("name", "Aula Valida", "floor", 2, "capacity", 25);

        ResponseEntity<String> resp = postQuery("/api/admin/rooms", tokenAdmin, params);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    // ==================== PrenotazioneRequest ====================

    @Test
    void blockRoomWithAMissingRoomIdIsRejectedByBeanValidation() throws Exception {
        Map<String, Object> params = Map.of(
                "startTime", LocalDateTime.now().plusDays(1).toString(),
                "endTime", LocalDateTime.now().plusDays(1).plusHours(1).toString());

        ResponseEntity<String> resp = postQuery("/api/bookings/block", tokenAdmin, params);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(TestJson.asMap(resp.getBody()).get("error")).isEqualTo("VALIDATION_ERROR");
    }

    // ============ security filter: full 401/403 bodies even without reaching a controller ============

    @Test
    void aProtectedEndpointWithoutATokenReturns401WithAJsonBody() throws Exception {
        ResponseEntity<String> resp = rest.exchange(
                "/api/rooms", HttpMethod.GET, HttpEntity.EMPTY, String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        Map<String, Object> body = TestJson.asMap(resp.getBody());
        assertThat(body.get("success")).isEqualTo(false);
        assertThat(body.get("userMessage")).isNotNull();
    }
}
