package com.classroom.booking.service;

import com.classroom.exception.BookingConflictException;
import com.classroom.exception.DomainConflictException;
import com.classroom.exception.ResourceNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import com.classroom.booking.model.Room;
import com.classroom.booking.model.RoomStatus;
import com.classroom.booking.model.Course;
import com.classroom.booking.model.Booking;
import com.classroom.booking.model.BookingStatus;
import com.classroom.booking.model.BookingOwner;
import com.classroom.booking.repository.RoomRepository;
import com.classroom.booking.repository.CourseRepository;
import com.classroom.booking.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for BookingService with its repositories mocked.
 *
 * The integration tests only exercise the happy path: covered here are all the rejection
 * branches (missing resource, permissions, occupied slot) that would be awkward or
 * impossible to provoke over HTTP, plus the room's status transitions.
 *
 * It sits in this package because the service's constructor is package-private.
 */
class BookingServiceUnitTest {

    private BookingRepository bookingRepository;
    private RoomRepository roomRepository;
    private CourseRepository courseRepository;
    private BookingService service;

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    @BeforeEach
    void setUp() {
        bookingRepository = mock(BookingRepository.class);
        roomRepository = mock(RoomRepository.class);
        courseRepository = mock(CourseRepository.class);
        service = new BookingService(bookingRepository, roomRepository, courseRepository);

        startTime = LocalDateTime.now().plusDays(1).withNano(0);
        endTime = startTime.plusHours(2);
    }

    // ---------- helpers ----------

    private Room room(Long id, RoomStatus status) {
        Room a = new Room();
        a.setId(id);
        a.setName("Aula " + id);
        a.setStatus(status);
        return a;
    }

    /**
     * The owner's snapshot. It used to be a User entity carrying a role: the role is not
     * needed here any more, because the service receives it from the caller as an isAdmin
     * flag instead of re-reading it from the database.
     */
    private BookingOwner user(Long id) {
        return new BookingOwner(id, "utente" + id, "Utente " + id);
    }

    private Booking booking(Long id, Room a, BookingOwner u, BookingStatus status) {
        Booking p = new Booking();
        p.setId(id);
        p.setRoom(a);
        p.setUser(u);
        p.setStartTime(startTime);
        p.setEndTime(endTime);
        p.setStatus(status);
        return p;
    }

    /** No conflict: the room comes out free for the requested period. */
    private void freeRoom() {
        when(bookingRepository.findConflictingBookings(anyLong(), any(), any()))
                .thenReturn(List.of());
    }

    @SuppressWarnings("null")
    private void saveAsGiven() {
        when(bookingRepository.save(any(Booking.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    // ==================== bookRoom ====================

    @Test
    @SuppressWarnings("null")
    void bookRoomRefusesWhenTheRoomIsBusy() {
        when(bookingRepository.findConflictingBookings(anyLong(), any(), any()))
                .thenReturn(List.of(booking(1L, room(10L, RoomStatus.FREE), user(1L), BookingStatus.BOOKED)));

        assertThatThrownBy(() -> service.bookRoom(10L, null, user(1L), startTime, endTime, "x"))
                .isInstanceOf(BookingConflictException.class);
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void bookRoomReportsAMissingRoom() {
        freeRoom();
        when(roomRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.bookRoom(10L, null, user(1L), startTime, endTime, "x"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // The "no such user" test went with the split: this service no longer consults the users
    // table, so it can no longer tell that case apart. Existence is guaranteed by the token
    // auth-service signed, within its expiry.

    @Test
    void bookRoomReportsAMissingCourse() {
        freeRoom();
        when(roomRepository.findById(10L)).thenReturn(Optional.of(room(10L, RoomStatus.FREE)));
        when(courseRepository.findById(77L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.bookRoom(10L, 77L, user(1L), startTime, endTime, "x"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void bookRoomAttachesTheCourseWhenPresent() {
        freeRoom();
        saveAsGiven();
        Course course = new Course();
        course.setId(77L);
        course.setName("Analisi 1");
        when(roomRepository.findById(10L)).thenReturn(Optional.of(room(10L, RoomStatus.FREE)));
        when(courseRepository.findById(77L)).thenReturn(Optional.of(course));
        when(bookingRepository.findActiveBookings(anyLong(), any())).thenReturn(List.of());

        Booking created = service.bookRoom(10L, 77L, user(1L), startTime, endTime, "con corso");

        assertThat(created).isNotNull();
        assertThat(created.getCourse()).isSameAs(course);
        assertThat(created.getStatus()).isEqualTo(BookingStatus.BOOKED);
    }

    @Test
    @SuppressWarnings("null")
    void bookRoomLeavesTheRoomStateUnchangedWhenTheBookingIsInTheFuture() {
        freeRoom();
        saveAsGiven();
        Room a = room(10L, RoomStatus.FREE);
        when(roomRepository.findById(10L)).thenReturn(Optional.of(a));
        // no booking active RIGHT NOW -> the status stays "free", no save on the room
        when(bookingRepository.findActiveBookings(anyLong(), any())).thenReturn(List.of());

        assertThat(service.bookRoom(10L, null, user(1L), startTime, endTime, "x")).isNotNull();
        verify(roomRepository, never()).save(any());
    }

    @Test
    void bookRoomMarksTheRoomBusyWhenTheBookingIsActiveNow() {
        freeRoom();
        saveAsGiven();
        Room a = room(10L, RoomStatus.FREE);
        when(roomRepository.findById(10L)).thenReturn(Optional.of(a));
        when(bookingRepository.findActiveBookings(anyLong(), any()))
                .thenReturn(List.of(booking(1L, a, user(1L), BookingStatus.BOOKED)));

        service.bookRoom(10L, null, user(1L), startTime, endTime, "x");

        assertThat(a.getStatus()).isEqualTo(RoomStatus.BUSY);
        verify(roomRepository).save(a);
    }

    @Test
    void bookRoomMarksTheRoomInMaintenanceWhenAMaintenanceBookingIsActive() {
        freeRoom();
        saveAsGiven();
        Room a = room(10L, RoomStatus.FREE);
        when(roomRepository.findById(10L)).thenReturn(Optional.of(a));
        when(bookingRepository.findActiveBookings(anyLong(), any()))
                .thenReturn(List.of(booking(1L, a, user(1L), BookingStatus.MAINTENANCE)));

        service.bookRoom(10L, null, user(1L), startTime, endTime, "x");

        assertThat(a.getStatus()).isEqualTo(RoomStatus.MAINTENANCE);
    }

    @Test
    void bookRoomMarksTheRoomBlockedWhenABlockingBookingIsActive() {
        freeRoom();
        saveAsGiven();
        Room a = room(10L, RoomStatus.FREE);
        when(roomRepository.findById(10L)).thenReturn(Optional.of(a));
        when(bookingRepository.findActiveBookings(anyLong(), any()))
                .thenReturn(List.of(booking(1L, a, user(1L), BookingStatus.BLOCKED)));

        service.bookRoom(10L, null, user(1L), startTime, endTime, "x");

        assertThat(a.getStatus()).isEqualTo(RoomStatus.BLOCKED);
    }

    // ==================== blockRoom ====================

    @Test
    void blockRoomRefusesWhenTheRoomIsBusy() {
        when(bookingRepository.findConflictingBookings(anyLong(), any(), any()))
                .thenReturn(List.of(booking(1L, room(10L, RoomStatus.FREE), user(1L), BookingStatus.BOOKED)));

        assertThatThrownBy(() -> service.blockRoom(10L, user(2L), startTime, endTime, "reason"))
                .isInstanceOf(BookingConflictException.class);
    }

    @Test
    void blockRoomReportsAMissingRoom() {
        freeRoom();
        when(roomRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.blockRoom(10L, user(2L), startTime, endTime, "reason"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // Removed: blockRoom no longer re-reads the role from the database. Non-admins are
    // filtered by @PreAuthorize("hasRole('ADMIN')") on the controller, with the role taken
    // from the token; checking it again here would mean calling auth-service.

    @Test
    void blockRoomCreatesABlockedBookingForTheAdmin() {
        freeRoom();
        saveAsGiven();
        when(roomRepository.findById(10L)).thenReturn(Optional.of(room(10L, RoomStatus.FREE)));

        Booking block = service.blockRoom(10L, user(2L), startTime, endTime, "manutenzione straordinaria");

        assertThat(block).isNotNull();
        assertThat(block.getStatus()).isEqualTo(BookingStatus.BLOCKED);
        assertThat(block.getCourse()).isNull();
        assertThat(block.getDescription()).isEqualTo("manutenzione straordinaria");
    }

    // ==================== cancelBooking ====================

    @Test
    void cancelReportsAMissingBooking() {
        when(bookingRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancelBooking(5L, 1L, false))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // Removed with the split: "no such user" is no longer a case this service can tell
    // apart, because it no longer consults the users table.

    @Test
    @SuppressWarnings("null")
    void cancelRefusesAnUnrelatedUser() {
        Room a = room(10L, RoomStatus.FREE);
        when(bookingRepository.findById(5L))
                .thenReturn(Optional.of(booking(5L, a, user(1L), BookingStatus.BOOKED)));

        assertThatThrownBy(() -> service.cancelBooking(5L, 9L, false))
                .isInstanceOf(AccessDeniedException.class);
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void cancelSucceedsForTheOwnerAndSetsTheStateToCancelled() {
        Room a = room(10L, RoomStatus.BUSY);
        Booking p = booking(5L, a, user(1L), BookingStatus.BOOKED);
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(p));
        when(roomRepository.findById(10L)).thenReturn(Optional.of(a));
        when(bookingRepository.findActiveBookings(anyLong(), any())).thenReturn(List.of());
        saveAsGiven();

        assertThat(service.cancelBooking(5L, 1L, false)).isTrue();
        assertThat(p.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        // the room goes back to free and is saved, because its status changed
        assertThat(a.getStatus()).isEqualTo(RoomStatus.FREE);
        verify(roomRepository).save(a);
    }

    @Test
    void cancelSucceedsForAnAdminOnSomeoneElsesBooking() {
        Room a = room(10L, RoomStatus.FREE);
        Booking p = booking(5L, a, user(1L), BookingStatus.BOOKED);
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(p));
        when(roomRepository.findById(10L)).thenReturn(Optional.of(a));
        when(bookingRepository.findActiveBookings(anyLong(), any())).thenReturn(List.of());
        saveAsGiven();

        assertThat(service.cancelBooking(5L, 2L, true)).isTrue();
    }

    @Test
    @SuppressWarnings("null")
    void cancelHandlesAMissingRoomDuringTheStateRefresh() {
        // the "room not found" branch inside updateRoomStatus
        Room a = room(10L, RoomStatus.FREE);
        Booking p = booking(5L, a, user(1L), BookingStatus.BOOKED);
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(p));
        when(roomRepository.findById(10L)).thenReturn(Optional.empty());
        saveAsGiven();

        assertThat(service.cancelBooking(5L, 1L, false)).isTrue();
        verify(roomRepository, never()).save(any());
    }

    @Test
    @SuppressWarnings("null")
    void cancelRefusesAnAlreadyCancelledBooking() {
        // Cancelling twice must not succeed: the caller would get a "cancelled
        // successfully" for an operation that changed nothing.
        Room a = room(10L, RoomStatus.FREE);
        Booking p = booking(5L, a, user(1L), BookingStatus.CANCELLED);
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.cancelBooking(5L, 1L, false))
                .isInstanceOf(DomainConflictException.class);
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void cancelRefusesAnAdministrativeBlock() {
        // Blocks and maintenance are admin business: they are cancelled through the
        // dedicated admin endpoint, not through DELETE /api/bookings/{id}.
        Room a = room(10L, RoomStatus.BLOCKED);
        Booking p = booking(5L, a, user(1L), BookingStatus.BLOCKED);
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.cancelBooking(5L, 1L, false))
                .isInstanceOf(DomainConflictException.class);
    }

    @Test
    void theRuleOnTheStatusAppliesToAdminsToo() {
        // The rule is about the status, not the role: to cancel an already cancelled
        // booking anyway, an admin has cancelBookingAsAdmin.
        Room a = room(10L, RoomStatus.FREE);
        Booking p = booking(5L, a, user(1L), BookingStatus.CANCELLED);
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(p));

        // isAdmin=true and not false: it used to be false, so what rejected the call was the
        // OWNERSHIP check, not the status rule the test claims to verify. With booleans the
        // two were indistinguishable and the test passed anyway; with typed exceptions the
        // difference shows, and the test now proves what it says it does.
        assertThatThrownBy(() -> service.cancelBooking(5L, 2L, true))
                .isInstanceOf(DomainConflictException.class);
    }

    // ==================== updateBooking ====================

    @Test
    void updateReportsAMissingBooking() {
        when(bookingRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateBooking(5L, 10L, null, 1L, false, startTime, endTime, "x"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // Removed: "no such user" is no longer a case this service can tell apart. It has not
    // consulted the users table since auth-service was split off, and existence is
    // guaranteed by the signed token, within its expiry.

    @Test
    void updateRefusesAnUnrelatedUser() {
        when(bookingRepository.findById(5L)).thenReturn(
                Optional.of(booking(5L, room(10L, RoomStatus.FREE), user(1L), BookingStatus.BOOKED)));

        assertThatThrownBy(() -> service.updateBooking(5L, 10L, null, 9L, false, startTime, endTime, "x"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void updateReportsAMissingRoom() {
        when(bookingRepository.findById(5L)).thenReturn(
                Optional.of(booking(5L, room(10L, RoomStatus.FREE), user(1L), BookingStatus.BOOKED)));
        when(roomRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateBooking(5L, 99L, null, 1L, false, startTime, endTime, "x"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateRefusesWhenTheNewTimeOverlaps() {
        Room a = room(10L, RoomStatus.FREE);
        when(bookingRepository.findById(5L)).thenReturn(
                Optional.of(booking(5L, a, user(1L), BookingStatus.BOOKED)));
        when(roomRepository.findById(10L)).thenReturn(Optional.of(a));
        when(bookingRepository.findConflictingBookingsExcluding(anyLong(), any(), any(), anyLong()))
                .thenReturn(List.of(booking(6L, a, user(2L), BookingStatus.BOOKED)));

        assertThatThrownBy(() -> service.updateBooking(5L, 10L, null, 1L, false, startTime, endTime, "x"))
                .isInstanceOf(BookingConflictException.class);
    }

    @Test
    void updateReportsAMissingCourse() {
        Room a = room(10L, RoomStatus.FREE);
        when(bookingRepository.findById(5L)).thenReturn(
                Optional.of(booking(5L, a, user(1L), BookingStatus.BOOKED)));
        when(roomRepository.findById(10L)).thenReturn(Optional.of(a));
        when(bookingRepository.findConflictingBookingsExcluding(anyLong(), any(), any(), anyLong()))
                .thenReturn(List.of());
        when(courseRepository.findById(77L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateBooking(5L, 10L, 77L, 1L, false, startTime, endTime, "x"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void theUpdateAppliesTheNewValuesForTheOwner() {
        Room oldRoom = room(10L, RoomStatus.FREE);
        Room newRoom = room(20L, RoomStatus.FREE);
        Booking p = booking(5L, oldRoom, user(1L), BookingStatus.BOOKED);
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(p));
        when(roomRepository.findById(20L)).thenReturn(Optional.of(newRoom));
        when(bookingRepository.findConflictingBookingsExcluding(anyLong(), any(), any(), anyLong()))
                .thenReturn(List.of());
        saveAsGiven();

        LocalDateTime newStart = startTime.plusDays(3);
        Booking updated = service.updateBooking(
                5L, 20L, null, 1L, false, newStart, newStart.plusHours(1), "nuova descrizione");

        assertThat(updated).isNotNull();
        assertThat(updated.getRoom()).isSameAs(newRoom);
        assertThat(updated.getStartTime()).isEqualTo(newStart);
        assertThat(updated.getDescription()).isEqualTo("nuova descrizione");
    }

    @Test
    void updateIsAllowedForAdminOnSomeoneElsesBooking() {
        Room a = room(10L, RoomStatus.FREE);
        when(bookingRepository.findById(5L)).thenReturn(
                Optional.of(booking(5L, a, user(1L), BookingStatus.BOOKED)));
        when(roomRepository.findById(10L)).thenReturn(Optional.of(a));
        when(bookingRepository.findConflictingBookingsExcluding(anyLong(), any(), any(), anyLong()))
                .thenReturn(List.of());
        saveAsGiven();

        assertThat(service.updateBooking(5L, 10L, null, 2L, true, startTime, endTime, "x")).isNotNull();
    }

    // ==================== getRoomStatus ====================

    @Test
    void roomStatusIsFreeWithNoActiveBookings() {
        when(bookingRepository.findActiveBookings(anyLong(), any())).thenReturn(List.of());

        assertThat(service.getRoomStatus(10L, LocalDateTime.now())).isEqualTo("FREE");
    }

    @Test
    void roomStatusIsBookedWithAnOrdinaryBooking() {
        Room a = room(10L, RoomStatus.BUSY);
        when(bookingRepository.findActiveBookings(anyLong(), any()))
                .thenReturn(List.of(booking(1L, a, user(1L), BookingStatus.BOOKED)));

        assertThat(service.getRoomStatus(10L, LocalDateTime.now())).isEqualTo("BOOKED");
    }

    @Test
    void roomStatusIsBlockedWhenABlockIsActive() {
        Room a = room(10L, RoomStatus.BLOCKED);
        when(bookingRepository.findActiveBookings(anyLong(), any()))
                .thenReturn(List.of(
                        booking(1L, a, user(1L), BookingStatus.BOOKED),
                        booking(2L, a, user(2L), BookingStatus.BLOCKED)));

        assertThat(service.getRoomStatus(10L, LocalDateTime.now())).isEqualTo("BLOCKED");
    }

    @Test
    void maintenanceWinsOverBlocked() {
        // the precedence the service declares: MAINTENANCE > BLOCKED > BOOKED
        Room a = room(10L, RoomStatus.MAINTENANCE);
        when(bookingRepository.findActiveBookings(anyLong(), any()))
                .thenReturn(List.of(
                        booking(1L, a, user(2L), BookingStatus.BLOCKED),
                        booking(2L, a, user(2L), BookingStatus.MAINTENANCE)));

        assertThat(service.getRoomStatus(10L, LocalDateTime.now())).isEqualTo("MAINTENANCE");
    }

}
