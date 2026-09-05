package com.prenotazioni.booking.repository;

import com.prenotazioni.booking.model.Booking;
import com.prenotazioni.booking.model.BookingOwner;
import com.prenotazioni.booking.model.BookingStatus;
import com.prenotazioni.booking.model.Room;
import com.prenotazioni.booking.model.RoomStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Four queries in BookingRepository exclude cancelled bookings with a literal written into
 * the JPQL: {@code AND p.status != 'cancelled'}. This class exists because that literal is
 * the one place where the stored status value is repeated in a form nothing checks.
 *
 * It is not a hypothetical. Translating the status values to English changed the stored
 * value from 'annullata' to 'cancelled' and left these four literals behind, and the whole
 * suite stayed green: no test until this one ever booked over a cancelled booking. The
 * effect on a user would have been a slot that stays unbookable after they cancel it -
 * silent, and with nothing in the logs to suggest why.
 *
 * A derived query would not have this problem, because Spring Data binds those to the enum.
 * These are @Query strings, and a string does not get checked by anything but a test.
 */
@SpringBootTest
@ActiveProfiles("test")
class BookingRepositoryStatusFilterTest {

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private RoomRepository roomRepository;

    private Long roomId;
    private LocalDateTime start;
    private LocalDateTime end;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        roomRepository.deleteAll();

        Room room = new Room();
        room.setName("Room Status Filter");
        room.setFloor(1);
        room.setCapacity(10);
        room.setVirtual(false);
        room.setStatus(RoomStatus.FREE);
        roomId = roomRepository.save(room).getId();

        start = LocalDateTime.now().plusDays(3).withNano(0);
        end = start.plusHours(2);
    }

    private Long save(BookingStatus status) {
        Booking booking = new Booking();
        booking.setRoom(roomRepository.findById(roomId).orElseThrow());
        booking.setUser(new BookingOwner(1L, "filter-user", "Filter User"));
        booking.setStartTime(start);
        booking.setEndTime(end);
        booking.setStatus(status);
        booking.setCreatedAt(LocalDateTime.now());
        return bookingRepository.save(booking).getId();
    }

    @Test
    void aCancelledBookingNoLongerBlocksItsSlot() {
        save(BookingStatus.CANCELLED);

        List<Booking> conflicts = bookingRepository.findConflictingBookings(roomId, start, end);

        assertThat(conflicts).isEmpty();
    }

    @Test
    void anActiveBookingStillBlocksItsSlot() {
        // The control for the test above: without it, a query that returned nothing at all
        // would look like a pass.
        save(BookingStatus.BOOKED);

        assertThat(bookingRepository.findConflictingBookings(roomId, start, end)).hasSize(1);
    }

    @Test
    void theExcludingVariantAlsoIgnoresCancelledBookings() {
        save(BookingStatus.CANCELLED);
        Long other = save(BookingStatus.BOOKED);

        // Excluding the only active booking has to leave nothing behind: the cancelled one
        // must not take its place as a conflict.
        assertThat(bookingRepository.findConflictingBookingsExcluding(roomId, start, end, other))
                .isEmpty();
    }

    @Test
    void aCancelledBookingIsNotAnActiveBookingAtThatMoment() {
        save(BookingStatus.CANCELLED);

        assertThat(bookingRepository.findActiveBookings(roomId, start.plusMinutes(30))).isEmpty();
    }

    @Test
    void aCancelledBookingIsNotAFutureBooking() {
        save(BookingStatus.CANCELLED);
        save(BookingStatus.BOOKED);

        List<Booking> future = bookingRepository.findFutureBookings(LocalDateTime.now());

        assertThat(future).hasSize(1);
        assertThat(future.get(0).getStatus()).isEqualTo(BookingStatus.BOOKED);
    }
}
