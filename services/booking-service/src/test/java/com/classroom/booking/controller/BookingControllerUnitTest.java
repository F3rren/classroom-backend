package com.classroom.booking.controller;

import com.classroom.dto.ApiEnvelope;
import com.classroom.booking.dto.BookingRequest;
import com.classroom.exception.BookingConflictException;
import com.classroom.exception.DomainConflictException;
import com.classroom.booking.model.Room;
import com.classroom.booking.model.Booking;
import com.classroom.booking.model.BookingStatus;
import com.classroom.booking.model.BookingOwner;
import com.classroom.security.AppPrincipal;
import com.classroom.booking.service.BookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for BookingController, with no Spring context.
 *
 * They cover the branches the integration tests cannot reach over HTTP:
 *  - the three DataIntegrityViolationException catches: on H2 the anti-overlap constraint
 *    "EXCLUDE USING gist" does not exist (it is Postgres-only, and the tests use
 *    ddl-auto=create-drop, so the schema comes from the entities), which means that
 *    exception can never fire over HTTP;
 *  - the date parsing and range branches, which sit below Bean Validation's threshold.
 *
 * It lives in this package and not in the root like the other tests, because the
 * controller's constructor is package-private.
 */
// The three cancelReturns404/403/409 tests went with the branches they covered: the
// controller no longer reconstructs the reason for a failure in order to choose the status.
// Those cases are now covered by BookingServiceUnitTest, which checks which exception is
// thrown, and by GlobalExceptionHandlerUnitTest, which checks what status it turns into.
// They used to be a single test because they were a single block of code.
class BookingControllerUnitTest {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private BookingService service;
    private BookingController controller;
    private AppPrincipal user;
    private AppPrincipal admin;

    @BeforeEach
    void setUp() {
        service = mock(BookingService.class);
        controller = new BookingController(service);
        user = new AppPrincipal(1L, "user@test.it", "m.rossi", "Mario Rossi", "user");
        admin = new AppPrincipal(2L, "admin@test.it", "m.rossi", "Mario Rossi", "admin");
    }

    // ---------- helpers ----------

    private BookingRequest request(String startTime, String endTime) {
        BookingRequest r = new BookingRequest();
        r.setRoomId(10L);
        r.setCourseId(null);
        r.setStartTime(startTime);
        r.setEndTime(endTime);
        r.setDescription("descrizione di test");
        return r;
    }

    private BookingRequest validRequest() {
        LocalDateTime startTime = LocalDateTime.now().plusDays(1).withNano(0);
        return request(startTime.format(ISO), startTime.plusHours(2).format(ISO));
    }

    private Booking fakeBooking() {
        Room room = new Room();
        room.setId(10L);
        room.setName("Aula Finta");
        BookingOwner u = new BookingOwner(1L, "utente", "Utente Test");
        Booking p = new Booking();
        p.setId(99L);
        p.setRoom(room);
        p.setUser(snapshotOf(u.getId(), u.getUsername(), u.getName()));
        p.setStartTime(LocalDateTime.now().plusDays(1));
        p.setEndTime(LocalDateTime.now().plusDays(1).plusHours(2));
        p.setStatus(BookingStatus.BOOKED);
        return p;
    }

    @SuppressWarnings("unchecked")
    private String errorCode(ResponseEntity<?> resp) {
        return ((ApiEnvelope<Object>) resp.getBody()).getError();
    }

    // ==================== bookRoom ====================

    @Test
    void bookRoomRejectsAnUnparsableStartDate() {
        ResponseEntity<?> resp = controller.bookRoom(request("non-una-data", "2030-01-01T12:00:00"), user);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(errorCode(resp)).isEqualTo("INVALID_START_DATE");
    }

    @Test
    void bookRoomRejectsAnUnparsableEndDate() {
        ResponseEntity<?> resp = controller.bookRoom(request("2030-01-01T10:00:00", "non-una-data"), user);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(errorCode(resp)).isEqualTo("INVALID_END_DATE");
    }

    @Test
    void bookRoomRejectsAnEndBeforeTheStart() {
        ResponseEntity<?> resp = controller.bookRoom(
                request("2030-01-01T12:00:00", "2030-01-01T10:00:00"), user);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(errorCode(resp)).isEqualTo("INVALID_DATE_RANGE");
    }

    @Test
    void bookRoomRejectsADateInThePast() {
        LocalDateTime past = LocalDateTime.now().minusDays(2).withNano(0);
        ResponseEntity<?> resp = controller.bookRoom(
                request(past.format(ISO), past.plusHours(1).format(ISO)), user);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(errorCode(resp)).isEqualTo("PAST_DATE");
    }

    @Test
    void bookRoomLetsTheConflictPropagate() {
        // The controller no longer translates: the exception's type already carries the
        // cause and GlobalExceptionHandler decides the status once. What is checked here is
        // that the controller does not intercept it, which is the correct behaviour.
        when(service.bookRoom(anyLong(), any(), any(), any(), any(), anyString())).thenThrow(new BookingConflictException("BOOKING_CONFLICT", "busy", "L'aula non e' disponibile."));

        assertThatThrownBy(() -> controller.bookRoom(validRequest(), user))
                .isInstanceOf(BookingConflictException.class);
    }

    @Test
    void bookRoomTranslatesADbConstraintIntoABookingConflict() {
        when(service.bookRoom(anyLong(), any(), any(), any(), any(), anyString()))
                .thenThrow(new DataIntegrityViolationException("bookings_no_overlap"));

        BookingRequest req = validRequest();
        assertThatThrownBy(() -> controller.bookRoom(req, user))
                .isInstanceOf(BookingConflictException.class)
                .satisfies(e -> assertThat(((BookingConflictException) e).getErrorCode()).isEqualTo("BOOKING_CONFLICT"));
    }

    @Test
    void bookRoomReturns201OnSuccess() {
        when(service.bookRoom(anyLong(), any(), any(), any(), any(), anyString()))
                .thenReturn(fakeBooking());

        ResponseEntity<?> resp = controller.bookRoom(validRequest(), user);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    // ==================== modificaPrenotazione (PUT) ====================

    @Test
    void updateRejectsAnUnparsableStartDate() {
        ResponseEntity<?> resp = controller.updateBooking(
                5L, request("boom", "2030-01-01T12:00:00"), user);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(errorCode(resp)).isEqualTo("INVALID_START_DATE");
    }

    @Test
    void updateRejectsAnUnparsableEndDate() {
        ResponseEntity<?> resp = controller.updateBooking(
                5L, request("2030-01-01T10:00:00", "boom"), user);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(errorCode(resp)).isEqualTo("INVALID_END_DATE");
    }

    @Test
    void updateRejectsAnEndBeforeTheStart() {
        ResponseEntity<?> resp = controller.updateBooking(
                5L, request("2030-01-01T12:00:00", "2030-01-01T10:00:00"), user);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(errorCode(resp)).isEqualTo("INVALID_DATE_RANGE");
    }

    @Test
    void updateRejectsADateInThePast() {
        LocalDateTime past = LocalDateTime.now().minusDays(2).withNano(0);
        ResponseEntity<?> resp = controller.updateBooking(
                5L, request(past.format(ISO), past.plusHours(1).format(ISO)), user);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(errorCode(resp)).isEqualTo("PAST_DATE");
    }

    @Test
    void updateLetsTheConflictPropagate() {
        // The controller no longer translates: the exception's type already carries the
        // cause and GlobalExceptionHandler decides the status once. What is checked here is
        // that the controller does not intercept it, which is the correct behaviour.
        when(service.updateBooking(anyLong(), anyLong(), any(), anyLong(), anyBoolean(), any(), any(), anyString())).thenThrow(new BookingConflictException("UPDATE_CONFLICT", "busy", "L'aula non e' disponibile."));

        assertThatThrownBy(() -> controller.updateBooking(5L, validRequest(), user))
                .isInstanceOf(BookingConflictException.class);
    }

    @Test
    void updateTranslatesADbConstraintIntoAnUpdateConflict() {
        when(service.updateBooking(anyLong(), anyLong(), any(), anyLong(), anyBoolean(), any(), any(), anyString()))
                .thenThrow(new DataIntegrityViolationException("bookings_no_overlap"));

        BookingRequest req = validRequest();
        assertThatThrownBy(() -> controller.updateBooking(5L, req, user))
                .isInstanceOf(BookingConflictException.class)
                .satisfies(e -> assertThat(((BookingConflictException) e).getErrorCode()).isEqualTo("UPDATE_CONFLICT"));
    }

    @Test
    void updateReturns200OnSuccess() {
        when(service.updateBooking(anyLong(), anyLong(), any(), anyLong(), anyBoolean(), any(), any(), anyString()))
                .thenReturn(fakeBooking());

        ResponseEntity<?> resp = controller.updateBooking(5L, validRequest(), user);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ==================== blockRoom ====================

    @Test
    void blockRejectsAnUnparsableStartDate() {
        ResponseEntity<?> resp = controller.blockRoom(request("boom", "2030-01-01T12:00:00"), admin);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(errorCode(resp)).isEqualTo("INVALID_START_DATE");
    }

    @Test
    void blockRejectsAnUnparsableEndDate() {
        ResponseEntity<?> resp = controller.blockRoom(request("2030-01-01T10:00:00", "boom"), admin);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(errorCode(resp)).isEqualTo("INVALID_END_DATE");
    }

    @Test
    void blockRejectsAnEndBeforeTheStart() {
        ResponseEntity<?> resp = controller.blockRoom(
                request("2030-01-01T12:00:00", "2030-01-01T10:00:00"), admin);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(errorCode(resp)).isEqualTo("INVALID_DATE_RANGE");
    }

    @Test
    void blockLetsTheConflictPropagate() {
        // The controller no longer translates: the exception's type already carries the
        // cause and GlobalExceptionHandler decides the status once. What is checked here is
        // that the controller does not intercept it, which is the correct behaviour.
        when(service.blockRoom(anyLong(), any(), any(), any(), anyString())).thenThrow(new BookingConflictException("BLOCK_CONFLICT", "busy", "L'aula non e' disponibile."));

        assertThatThrownBy(() -> controller.blockRoom(validRequest(), admin))
                .isInstanceOf(BookingConflictException.class);
    }

    @Test
    void blockTranslatesADbConstraintIntoABlockConflict() {
        when(service.blockRoom(anyLong(), any(), any(), any(), anyString()))
                .thenThrow(new DataIntegrityViolationException("bookings_no_overlap"));

        BookingRequest req = validRequest();
        assertThatThrownBy(() -> controller.blockRoom(req, admin))
                .isInstanceOf(BookingConflictException.class)
                .satisfies(e -> assertThat(((BookingConflictException) e).getErrorCode()).isEqualTo("BLOCK_CONFLICT"));
    }

    @Test
    void blockReturns201OnSuccess() {
        when(service.blockRoom(anyLong(), any(), any(), any(), anyString()))
                .thenReturn(fakeBooking());

        ResponseEntity<?> resp = controller.blockRoom(validRequest(), admin);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    // ==================== annullaPrenotazione (DELETE) ====================

    @Test
    void anAdminOnSomeoneElsesAlreadyCancelledBookingGetsTheConflictNotAForbidden() {
        // This test was born to guard against a misleading 403: the controller re-derived
        // the ownership rule and, if it got the order of the checks wrong, an admin
        // cancelling somebody else's already cancelled booking was told "you can only cancel
        // your own". That duplication is gone: the service decides, and throws the conflict
        // on the status. This stays to pin down that the controller does not reintroduce an
        // interpretation of its own.
        when(service.cancelBooking(7L, 2L, true))
                .thenThrow(new DomainConflictException("INVALID_STATE", "already cancelled",
                        "Questa prenotazione non puo' essere annullata nello stato attuale."));

        assertThatThrownBy(() -> controller.cancelBooking(7L, admin))
                .isInstanceOf(DomainConflictException.class);
    }

    @Test
    void cancelReturns200ForTheOwner() {
        when(service.getBookingById(7L)).thenReturn(fakeBooking());
        when(service.cancelBooking(7L, 1L, false)).thenReturn(true);

        ResponseEntity<?> resp = controller.cancelBooking(7L, user);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    /** The owner's snapshot, built by hand now: the users table is not here any more. */
    private static BookingOwner snapshotOf(Long id, String username, String name) {
        return new BookingOwner(id, username, name);
    }
}
