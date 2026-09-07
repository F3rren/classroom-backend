package com.prenotazioni.booking.security;

import com.prenotazioni.booking.model.Room;
import com.prenotazioni.booking.model.Booking;
import com.prenotazioni.security.AppPrincipal;
import com.prenotazioni.booking.model.BookingOwner;
import com.prenotazioni.booking.service.BookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bean usato da @PreAuthorize sugli endpoint di lettura delle prenotazioni.
 * I test HTTP coprono proprietario e estraneo; qui si aggiungono i due guard
 * (no principal, no such booking) that cannot be produced through a request.
 */
class BookingAuthorizationServiceUnitTest {

    private BookingService bookingService;
    private BookingAuthorizationService auth;

    @BeforeEach
    void setUp() {
        bookingService = mock(BookingService.class);
        auth = new BookingAuthorizationService(bookingService);
    }

    private Booking bookingOf(Long ownerId) {
        BookingOwner u = new BookingOwner(ownerId, "u" + ownerId, "Utente");
        Room a = new Room();
        a.setId(1L);
        Booking p = new Booking();
        p.setId(5L);
        p.setUser(snapshotOf(u.getId(), u.getUsername(), u.getName()));
        p.setRoom(a);
        return p;
    }

    @Test
    void deniesWhenThereIsNoAuthenticatedUser() {
        assertThat(auth.isOwnerOrAdmin(5L, null)).isFalse();
    }

    @Test
    void allowsWhenBookingDoesNotExistSoTheControllerCanReturn404() {
        // a deliberate choice: a 404 is not masked with a 403
        when(bookingService.getBookingById(5L)).thenReturn(null);

        assertThat(auth.isOwnerOrAdmin(5L, new AppPrincipal(1L, "u@test.it", "m.rossi", "Mario Rossi", "user"))).isTrue();
    }

    @Test
    void allowsTheOwner() {
        when(bookingService.getBookingById(5L)).thenReturn(bookingOf(1L));

        assertThat(auth.isOwnerOrAdmin(5L, new AppPrincipal(1L, "u@test.it", "m.rossi", "Mario Rossi", "user"))).isTrue();
    }

    @Test
    void deniesAnUnrelatedUser() {
        when(bookingService.getBookingById(5L)).thenReturn(bookingOf(1L));

        assertThat(auth.isOwnerOrAdmin(5L, new AppPrincipal(99L, "altro@test.it", "m.rossi", "Mario Rossi", "user"))).isFalse();
    }

    @Test
    void allowsAnAdminOnSomeoneElsesBooking() {
        when(bookingService.getBookingById(5L)).thenReturn(bookingOf(1L));

        assertThat(auth.isOwnerOrAdmin(5L, new AppPrincipal(2L, "admin@test.it", "m.rossi", "Mario Rossi", "admin"))).isTrue();
    }

    /** The owner's snapshot, built by hand now: the users table is not here any more. */
    private static BookingOwner snapshotOf(Long id, String username, String name) {
        return new BookingOwner(id, username, name);
    }
}
