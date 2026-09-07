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
import com.classroom.events.BookingCancelledEvent;
import com.classroom.booking.messaging.EventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

/**
 * Covers the admin endpoints that had no test until now: room management (GET/PUT/DELETE on
 * /api/admin/rooms) and booking management from the admin side.
 *
 * For every destructive operation the test does not stop at the status code but checks the
 * real effect on the database, and there is always the counter-test that a user who is not
 * admin riceve 403 sullo stesso endpoint.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AdminManagementTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private BookingRepository bookingRepository;

    /**
     * The notification is no longer a row written in this process but a call to
     * notification-service. The test keeps its intent by checking that the call goes out:
     * that is the right boundary to check from here, and it does not require the other
     * service to be running.
     */
    @MockBean
    private EventPublisher eventPublisher;

    private String tokenAdmin;
    private String tokenUser;
    private Long roomId;
    private Long regularUserId;
    private Long bookingId;

    /** The ids no longer come from an insert: the test picks them and signs them into the token. */
    private static final Long ADMIN_ID = 1L;
    private static final Long REGULAR_USER_ID = 2L;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        roomRepository.deleteAll();

        // No user to create: the users table belongs to auth-service, and the tokens are
        // signed locally with the same secret (see TestJwt).
        regularUserId = REGULAR_USER_ID;

        Room room = new Room();
        room.setName("Aula Admin");
        room.setFloor(1);
        room.setCapacity(25);
        room.setVirtual(false);
        room.setStatus(RoomStatus.FREE);
        roomId = roomRepository.save(room).getId();

        Booking p = new Booking();
        p.setRoom(room);
        p.setUser(new BookingOwner(regularUserId, "user-mgmt", "User Mgmt"));
        p.setStartTime(LocalDateTime.now().plusDays(3).withNano(0));
        p.setEndTime(LocalDateTime.now().plusDays(3).plusHours(2).withNano(0));
        p.setStatus(BookingStatus.BOOKED);
        p.setDescription("Prenotazione gestita da admin");
        p.setCreatedAt(LocalDateTime.now());
        bookingId = bookingRepository.save(p).getId();

        tokenAdmin = TestJwt.forAdmin(ADMIN_ID, "admin-mgmt@test.it");
        tokenUser = TestJwt.forUser(REGULAR_USER_ID, "user-mgmt@test.it", "User Mgmt");
    }


    @SuppressWarnings("unchecked")
    private HttpHeaders bearer(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private ResponseEntity<String> exchange(String url, HttpMethod method, String token, Object body) {
        HttpEntity<Object> entity = body == null
                ? new HttpEntity<>(bearer(token))
                : new HttpEntity<>(body, bearer(token));
        return rest.exchange(url, method, entity, String.class);
    }


    @SuppressWarnings("unchecked")
    private Map<String, Object> dataOf(ResponseEntity<String> resp) throws Exception {
        return (Map<String, Object>) TestJson.asMap(resp.getBody()).get("data");
    }

    // ==================== room management ====================

    @Test
    void theAdminListsTheRooms() throws Exception {
        ResponseEntity<String> resp = exchange("/api/admin/rooms", HttpMethod.GET, tokenAdmin, null);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dataOf(resp).get("totalRooms")).isEqualTo(1);
    }

    @Test
    void theAdminReadsARoomWrappedInTheRoomKey() throws Exception {
        ResponseEntity<String> resp = exchange("/api/admin/rooms/" + roomId, HttpMethod.GET, tokenAdmin, null);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dataOf(resp).keySet()).containsExactly("room");
    }

    @Test
    void theAdminGets404OnAMissingRoom() throws Exception {
        ResponseEntity<String> resp = exchange("/api/admin/rooms/999999", HttpMethod.GET, tokenAdmin, null);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(TestJson.asMap(resp.getBody()).get("error")).isEqualTo("ROOM_NOT_FOUND");
    }

    @Test
    void adminUpdatesRoomAndChangeIsPersisted() throws Exception {
        Map<String, Object> body = Map.of("name", "Aula Rinominata", "capacity", 42, "floor", 4);
        ResponseEntity<String> resp = exchange("/api/admin/rooms/" + roomId, HttpMethod.PUT, tokenAdmin, body);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dataOf(resp).get("name")).isEqualTo("Aula Rinominata");

        Room reloaded = roomRepository.findById(roomId).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo("Aula Rinominata");
        assertThat(reloaded.getCapacity()).isEqualTo(42);
    }

    @Test
    void updatingARoomRejectsAnInvalidBody() {
        // a negative capacity violates @Positive on RoomRequest
        Map<String, Object> body = Map.of("name", "X", "capacity", -5, "floor", 1);
        ResponseEntity<String> resp = exchange("/api/admin/rooms/" + roomId, HttpMethod.PUT, tokenAdmin, body);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void adminDeletesRoomAndItDisappears() {
        bookingRepository.deleteAll(); // the room has a booking attached to it
        ResponseEntity<String> resp = exchange("/api/admin/rooms/" + roomId, HttpMethod.DELETE, tokenAdmin, null);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(roomRepository.existsById(roomId)).isFalse();
    }

    @Test
    void adminDeleteRoomNotFoundReturns404() {
        ResponseEntity<String> resp = exchange("/api/admin/rooms/999999", HttpMethod.DELETE, tokenAdmin, null);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ==================== booking management ====================

    @Test
    void theAdminListsEveryBookingWithTheStatistics() throws Exception {
        ResponseEntity<String> resp = exchange("/api/admin/bookings", HttpMethod.GET, tokenAdmin, null);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> data = dataOf(resp);
        assertThat(data.keySet()).containsExactlyInAnyOrder("bookings", "stats");

        @SuppressWarnings("unchecked")
        Map<String, Object> stats = (Map<String, Object>) data.get("stats");
        assertThat(stats.keySet()).containsExactlyInAnyOrder("total", "active", "cancelled");
        assertThat(stats.get("total")).isEqualTo(1);
        assertThat(stats.get("active")).isEqualTo(1);
        assertThat(stats.get("cancelled")).isEqualTo(0);
    }

    @Test
    void adminForceDeletesAnyBookingAndNotifiesOwner() throws Exception {
        ResponseEntity<String> resp = exchange(
                "/api/admin/bookings/" + bookingId, HttpMethod.DELETE, tokenAdmin,
                Map.of("reason", "Aula richiesta per un esame"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> data = dataOf(resp);
        assertThat(data.get("adminAction")).isEqualTo(true);
        assertThat(data.get("reason")).isEqualTo("Aula richiesta per un esame");

        // the booking comes out cancelled and the owner gets a notification
        Booking after = bookingRepository.findById(bookingId).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        verify(eventPublisher).publishCancellation(any(BookingCancelledEvent.class));
    }

    @Test
    void anOverlongReasonIsRejectedInsteadOfSilentlyLosingTheNotification() throws Exception {
        // The reason ends up concatenated into Notification.message, which is varchar(1000).
        // Without a limit the notification save blows up and AdminController swallows the
        // exception ("a failed notification does not hold up the operation"): the booking
        // comes out cancelled but the owner is NEVER told, silently.
        String hugeReason = "x".repeat(1500);

        ResponseEntity<String> resp = exchange(
                "/api/admin/bookings/" + bookingId, HttpMethod.DELETE, tokenAdmin,
                Map.of("reason", hugeReason));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        // and the booking must NOT have been cancelled by a request that was refused
        assertThat(bookingRepository.findById(bookingId).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.BOOKED);
    }

    @Test
    void aReasonWithinTheLimitStillNotifiesTheOwner() {
        String longButValidReason = "y".repeat(400);

        ResponseEntity<String> resp = exchange(
                "/api/admin/bookings/" + bookingId, HttpMethod.DELETE, tokenAdmin,
                Map.of("reason", longButValidReason));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        // the notification has to really exist, not be lost to a silent catch
        verify(eventPublisher).publishCancellation(any(BookingCancelledEvent.class));
    }

    @Test
    void adminForceDeleteWorksWithoutBody() {
        // the body carrying the reason is optional: without it a default reason is used
        ResponseEntity<String> resp = exchange(
                "/api/admin/bookings/" + bookingId, HttpMethod.DELETE, tokenAdmin, null);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void adminForceDeleteOnMissingBookingReturns404() {
        ResponseEntity<String> resp = exchange(
                "/api/admin/bookings/999999", HttpMethod.DELETE, tokenAdmin, null);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ==================== counter-test: no access without the admin role ====================

    @Test
    void nonAdminIsForbiddenOnEveryAdminEndpoint() {
        record Call(String url, HttpMethod method) {}
        Call[] calls = {
                new Call("/api/admin/rooms", HttpMethod.GET),
                new Call("/api/admin/rooms/" + roomId, HttpMethod.GET),
                new Call("/api/admin/rooms/" + roomId, HttpMethod.DELETE),
                new Call("/api/admin/bookings", HttpMethod.GET),
                new Call("/api/admin/bookings/" + bookingId, HttpMethod.DELETE),
                // /api/admin/users/{id} is no longer served by this service: user
                // management moved to auth-service.
        };

        for (Call c : calls) {
            ResponseEntity<String> resp = exchange(c.url(), c.method(), tokenUser, null);
            assertThat(resp.getStatusCode())
                    .as("%s %s with a non-admin token", c.method(), c.url())
                    .isEqualTo(HttpStatus.FORBIDDEN);
        }

        // and nothing was changed
        assertThat(roomRepository.existsById(roomId)).isTrue();
    }



    @Test
    void adminCreateRoomRejectsDuplicateName() throws Exception {
        Map<String, Object> body = Map.of("name", "Aula Admin", "capacity", 10, "floor", 1);

        ResponseEntity<String> resp = exchange("/api/admin/rooms", HttpMethod.POST, tokenAdmin, body);

        // 409 and no longer 400: a name already taken is not a malformed request, and the
        // caller does not fix it by correcting the syntax. The code now says which of the
        // many failure reasons it was, instead of a generic ROOM_CREATION_FAILED.
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(TestJson.asMap(resp.getBody()).get("error")).isEqualTo("ROOM_NAME_TAKEN");
    }

    @Test
    void updatingWithAnInvalidIdIsRejected() throws Exception {
        Map<String, Object> body = Map.of("name", "Qualsiasi", "capacity", 10, "floor", 1);

        ResponseEntity<String> resp = exchange("/api/admin/rooms/0", HttpMethod.PUT, tokenAdmin, body);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(TestJson.asMap(resp.getBody()).get("error")).isEqualTo("INVALID_ROOM_ID");
    }

    @Test
    void updatingAMissingRoomAnswers404() throws Exception {
        Map<String, Object> body = Map.of("name", "Inesistente", "capacity", 10, "floor", 1);

        ResponseEntity<String> resp = exchange("/api/admin/rooms/999999", HttpMethod.PUT, tokenAdmin, body);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        // ROOM_NOT_FOUND and not ROOM_UPDATE_FAILED: the status was already 404, but the
        // code said "update failed" without saying why. It now names the cause.
        assertThat(TestJson.asMap(resp.getBody()).get("error")).isEqualTo("ROOM_NOT_FOUND");
    }

    @Test
    void theAdminEndpointsRequireAuthentication() {
        ResponseEntity<String> resp = rest.exchange(
                "/api/admin/rooms", HttpMethod.GET, HttpEntity.EMPTY, String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

}
