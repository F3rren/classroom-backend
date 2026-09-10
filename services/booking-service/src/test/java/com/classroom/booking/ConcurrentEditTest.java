package com.classroom.booking;

import com.classroom.booking.model.Booking;
import com.classroom.booking.model.BookingOwner;
import com.classroom.booking.model.BookingStatus;
import com.classroom.booking.model.Room;
import com.classroom.booking.model.RoomStatus;
import com.classroom.booking.repository.BookingRepository;
import com.classroom.booking.repository.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Two people editing the same row at once.
 *
 * Editing a room and editing a booking are read-modify-write across two transactions: load,
 * change some fields, save. Without a version column both writers succeeded and the later
 * one silently replaced the earlier - no error, no trace, the first editor's change simply
 * gone. It is the only defect of the review that lost data rather than answering wrongly.
 *
 * It cannot be reproduced through HTTP: a second request re-reads the row and finds the
 * fresh version, so it never collides. What produces the collision is two loads before
 * either save, which is what genuine concurrency does and what these tests do by hand -
 * every repository call here runs in its own transaction, so the two loads really are two
 * detached copies.
 */
@SpringBootTest
@ActiveProfiles("test")
class ConcurrentEditTest {

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private BookingRepository bookingRepository;

    private Long roomId;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        roomRepository.deleteAll();

        Room room = new Room();
        room.setName("Aula Contesa");
        room.setFloor(1);
        room.setCapacity(30);
        room.setVirtual(false);
        room.setStatus(RoomStatus.FREE);
        roomId = roomRepository.save(room).getId();
    }

    private Booking newBooking() {
        Booking booking = new Booking();
        booking.setRoom(roomRepository.findById(Objects.requireNonNull(roomId)).orElseThrow());
        booking.setUser(new BookingOwner(1L, "utente", "Utente Test"));
        booking.setStartTime(LocalDateTime.now().plusDays(1));
        booking.setEndTime(LocalDateTime.now().plusDays(1).plusHours(2));
        booking.setStatus(BookingStatus.BOOKED);
        booking.setCreatedAt(LocalDateTime.now());
        return bookingRepository.save(booking);
    }

    @Test
    void theSecondEditOfTheSameRoomIsRefusedInsteadOfWinningInSilence() {
        Room firstEditor = roomRepository.findById(Objects.requireNonNull(roomId)).orElseThrow();
        Room secondEditor = roomRepository.findById(Objects.requireNonNull(roomId)).orElseThrow();

        firstEditor.setName("Rinominata dal primo");
        roomRepository.save(firstEditor);

        secondEditor.setCapacity(99);
        assertThatThrownBy(() -> roomRepository.save(secondEditor))
                .isInstanceOf(OptimisticLockingFailureException.class);

        // The first editor's change is still there, which is the whole point: it used to be
        // replaced by whatever the second editor happened to be holding.
        assertThat(roomRepository.findById(roomId).orElseThrow().getName())
                .isEqualTo("Rinominata dal primo");
    }

    @Test
    void theSecondEditOfTheSameBookingIsRefusedToo() {
        Long bookingId = newBooking().getId();

        Booking firstEditor = bookingRepository.findById(Objects.requireNonNull(bookingId)).orElseThrow();
        Booking secondEditor = bookingRepository.findById(bookingId).orElseThrow();

        firstEditor.setDescription("Modificata dal primo");
        bookingRepository.save(firstEditor);

        secondEditor.setDescription("Modificata dal secondo");
        assertThatThrownBy(() -> bookingRepository.save(secondEditor))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void anEditOnTopOfAFreshReadStillWorks() {
        // The check must only refuse a STALE write. Sequential edits, which is what a single
        // user doing two things in a row produces, have to keep working.
        Room room = roomRepository.findById(Objects.requireNonNull(roomId)).orElseThrow();
        room.setName("Primo nome");
        roomRepository.save(room);

        Room reread = roomRepository.findById(roomId).orElseThrow();
        reread.setName("Secondo nome");

        assertThatCode(() -> roomRepository.save(reread)).doesNotThrowAnyException();
        assertThat(roomRepository.findById(roomId).orElseThrow().getName()).isEqualTo("Secondo nome");
    }

    @Test
    void refreshingTheDerivedStatusNeverCollidesWithAnEditInFlight() {
        // updateStatus deliberately bypasses the version, because room.status is a cache of
        // what the bookings say and not anybody's edit. Were it to go through save(), two
        // people booking DIFFERENT slots in the same room at the same moment would collide,
        // and one would lose a perfectly valid booking to a 409 raised by bookkeeping
        // neither of them asked for.
        Room editorHolding = roomRepository.findById(Objects.requireNonNull(roomId)).orElseThrow();

        roomRepository.updateStatus(roomId, RoomStatus.BUSY);
        assertThat(roomRepository.findById(roomId).orElseThrow().getStatus()).isEqualTo(RoomStatus.BUSY);

        // The edit loaded before the refresh still goes through: the refresh did not bump
        // the version, so it did not invalidate anybody.
        editorHolding.setCapacity(55);
        assertThatCode(() -> roomRepository.save(editorHolding)).doesNotThrowAnyException();
    }
}
