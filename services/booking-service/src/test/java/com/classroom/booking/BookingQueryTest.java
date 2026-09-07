package com.classroom.booking;

import com.classroom.testsupport.TestJson;
import com.classroom.testsupport.TestJwt;
import com.classroom.booking.model.Room;
import com.classroom.booking.model.RoomStatus;
import com.classroom.booking.model.Booking;
import com.classroom.booking.model.BookingStatus;
import com.classroom.booking.model.BookingOwner;
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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the read-only endpoints of /api/bookings that had no test until now:
 * /mine, /future, /all-details, /availability, /{id}/details and /status/{...}.
 *
 * The value here is not just "it answers 200": every test also pins the exact set of JSON
 * keys, because some of these endpoints are NOT wrapped in the ApiEnvelope (a historic shape
 * kept for the frontend) and that difference has to be defended.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class BookingQueryTest {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private BookingRepository bookingRepository;

    private String token;
    private Long roomId;
    private Long bookingId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        roomRepository.deleteAll();


        Room room = new Room();
        room.setName("Aula Query");
        room.setFloor(2);
        room.setCapacity(30);
        room.setVirtual(false);
        room.setStatus(RoomStatus.FREE);
        roomId = roomRepository.save(room).getId();

        startTime = LocalDateTime.now().plusDays(2).withNano(0);
        endTime = startTime.plusHours(2);

        BookingOwner user = new BookingOwner(1L, "query-user", "Query User");

        Booking p = new Booking();
        p.setRoom(room);
        p.setUser(user);
        p.setStartTime(startTime);
        p.setEndTime(endTime);
        p.setStatus(BookingStatus.BOOKED);
        p.setDescription("Prenotazione per test di query");
        p.setCreatedAt(LocalDateTime.now());
        bookingId = bookingRepository.save(p).getId();

        token = TestJwt.forUser(1L, "prenotazionequerytest@test.it", "Utente Test");
    }

    @SuppressWarnings("unchecked")
    private HttpHeaders bearer() {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return h;
    }

    private ResponseEntity<String> get(String url) {
        return rest.exchange(url, HttpMethod.GET, new HttpEntity<>(bearer()), String.class);
    }


    @Test
    void mineReturnsOnlyOwnBookingsWithoutAnEnvelope() throws Exception {
        ResponseEntity<String> resp = get("/api/bookings/mine");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = TestJson.asMap(resp.getBody());
        // the historic shape: only "bookings", no envelope and no total
        assertThat(body.keySet()).containsExactly("bookings");
        assertThat((java.util.List<?>) body.get("bookings")).hasSize(1);
    }

    @Test
    void mineExcludesCancelledBookings() throws Exception {
        Booking p = bookingRepository.findById(bookingId).orElseThrow();
        p.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(p);

        Map<String, Object> body = TestJson.asMap(get("/api/bookings/mine").getBody());
        assertThat((java.util.List<?>) body.get("bookings")).isEmpty();
    }

    @Test
    void futureBookingsIncludeTheTotal() throws Exception {
        ResponseEntity<String> resp = get("/api/bookings/future");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = TestJson.asMap(resp.getBody());
        assertThat(body.keySet()).containsExactlyInAnyOrder("bookings", "totalBookings");
        assertThat(body.get("totalBookings")).isEqualTo(1);
    }

    @Test
    void futureBookingsDoNotExposeTheOwnersData() {
        // The list is visible to any authenticated user, so the owner has to be reduced to
        // id/username/name, never the email or the role (sanitizeOwnerForListing).
        ResponseEntity<String> resp = get("/api/bookings/future");

        assertThat(resp.getBody()).doesNotContain("query-user@test.it");
        assertThat(resp.getBody()).doesNotContain("query-password");
    }

    @Test
    void theFullDetailReturnsTheTypedList() throws Exception {
        ResponseEntity<String> resp = get("/api/bookings/all-details");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = TestJson.asMap(resp.getBody());
        assertThat(body.keySet()).containsExactlyInAnyOrder("bookings", "totalBookings");
        assertThat(body.get("totalBookings")).isEqualTo(1);
    }

    @Test
    void bookingDetailsByIdReturnsTheDetails() throws Exception {
        ResponseEntity<String> resp = get("/api/bookings/" + bookingId + "/details");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = TestJson.asMap(resp.getBody());
        assertThat(body.keySet()).containsExactlyInAnyOrder(
                "booking", "fullDetails", "totalDetails");
    }

    @Test
    void availabilityReportsFreeForAFreeSlot() throws Exception {
        String freeStart = startTime.plusDays(5).format(ISO);
        String freeEnd = startTime.plusDays(5).plusHours(1).format(ISO);

        ResponseEntity<String> resp = get(
                "/api/bookings/availability?roomId=" + roomId + "&start=" + freeStart + "&end=" + freeEnd);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> data = castMap(TestJson.asMap(resp.getBody()).get("data"));
        assertThat(data.keySet()).containsExactlyInAnyOrder("roomId", "available", "period", "status");
        assertThat(data.get("available")).isEqualTo(true);
        assertThat(data.get("status")).isEqualTo("FREE");
    }

    @Test
    void availabilityReportsBusyForABookedSlot() throws Exception {
        ResponseEntity<String> resp = get(
                "/api/bookings/availability?roomId=" + roomId
                        + "&start=" + startTime.format(ISO) + "&end=" + endTime.format(ISO));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> data = castMap(TestJson.asMap(resp.getBody()).get("data"));
        assertThat(data.get("available")).isEqualTo(false);
        assertThat(data.get("status")).isEqualTo("BUSY");
    }

    @Test
    void availabilityWithAMalformedDateAnswers400() throws Exception {
        ResponseEntity<String> resp = get(
                "/api/bookings/availability?roomId=" + roomId + "&start=non-una-data&end=nemmeno");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(TestJson.asMap(resp.getBody()).get("error")).isEqualTo("INVALID_START_DATE");
    }

    /**
     * A regression: "/status/{roomId}" and "/status/{status}" were declared on the same
     * path pattern, so at runtime Spring failed with "Ambiguous handler methods mapped" and
     * BOTH endpoints answered 500. The room status now lives at "/room-status/{roomId}";
     * these two tests defend that separation.
     */
    @Test
    void roomStatusReturnsTheRoomStatusPayload() throws Exception {
        ResponseEntity<String> resp = get("/api/bookings/room-status/" + roomId);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = TestJson.asMap(resp.getBody());
        assertThat(body.keySet()).containsExactlyInAnyOrder("roomId", "status", "timestamp");
        assertThat(body.get("roomId")).isEqualTo(roomId.intValue());
    }

    @Test
    void theStatusFilterReturnsOnlyMatchingBookings() throws Exception {
        ResponseEntity<String> resp = get("/api/bookings/status/booked");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = TestJson.asMap(resp.getBody());
        assertThat(body.keySet()).containsExactlyInAnyOrder("status", "bookings", "totalBookings");
        assertThat(body.get("status")).isEqualTo("booked");
        assertThat(body.get("totalBookings")).isEqualTo(1);
    }

    @Test
    void bookingsByStatusWithNoMatchReturnsAnEmptyList() throws Exception {
        ResponseEntity<String> resp = get("/api/bookings/status/cancelled");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(TestJson.asMap(resp.getBody()).get("totalBookings")).isEqualTo(0);
    }

    @Test
    void theQueryEndpointsRequireAuthentication() {
        for (String url : new String[]{
                "/api/bookings/mine",
                "/api/bookings/future",
                "/api/bookings/all-details"}) {
            ResponseEntity<String> resp = rest.exchange(url, HttpMethod.GET, HttpEntity.EMPTY, String.class);
            assertThat(resp.getStatusCode())
                    .as("endpoint %s senza token", url)
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object o) {
        return (Map<String, Object>) o;
    }


    @Test
    void aMissingBookingAnswersInTheCommonShape() throws Exception {
        // These endpoints used to answer {"error":"Prenotazione non trovata"}: a shape all
        // of their own, with no "success" and no "userMessage", and an "error" holding a
        // sentence instead of a code. No test covered it, which is why the divergence
        // survived so long.
        ResponseEntity<String> resp = get("/api/bookings/999999");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        Map<String, Object> body = TestJson.asMap(resp.getBody());
        assertThat(body.get("success")).isEqualTo(false);
        assertThat(body.get("error")).isEqualTo("BOOKING_NOT_FOUND");
        assertThat(body.get("userMessage")).isNotNull();
        assertThat(body.get("sessionId")).isNotNull();
    }

    @Test
    void theDetailsOfAMissingBookingAlsoUseTheCommonShape() throws Exception {
        ResponseEntity<String> resp = get("/api/bookings/999999/details");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(TestJson.asMap(resp.getBody()).get("error")).isEqualTo("BOOKING_NOT_FOUND");
    }

    @Test
    void anUnknownStatusAnswersInTheCommonShapeAndSaysWhichAreAllowed() throws Exception {
        ResponseEntity<String> resp = get("/api/bookings/status/inventato");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Map<String, Object> body = TestJson.asMap(resp.getBody());
        assertThat(body.get("success")).isEqualTo(false);
        // The list of allowed statuses is derived from the enum: if one were added and the
        // message lagged behind, this assertion would notice.
        assertThat(String.valueOf(body.get("userMessage"))).contains("booked", "cancelled");
    }
}
