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
 * (principal assente, prenotazione inesistente) non producibili via richiesta.
 */
class BookingAuthorizationServiceUnitTest {

    private BookingService bookingService;
    private BookingAuthorizationService auth;

    @BeforeEach
    void setUp() {
        bookingService = mock(BookingService.class);
        auth = new BookingAuthorizationService(bookingService);
    }

    private Booking prenotazioneDi(Long proprietarioId) {
        BookingOwner u = new BookingOwner(proprietarioId, "u" + proprietarioId, "Utente");
        Room a = new Room();
        a.setId(1L);
        Booking p = new Booking();
        p.setId(5L);
        p.setUser(istantaneaDi(u.getId(), u.getUsername(), u.getName()));
        p.setRoom(a);
        return p;
    }

    @Test
    void negaSeNonCEUnUtenteAutenticato() {
        assertThat(auth.isOwnerOrAdmin(5L, null)).isFalse();
    }

    @Test
    void allowsWhenBookingDoesNotExistSoTheControllerCanReturn404() {
        // scelta deliberata: non si maschera un 404 con un 403
        when(bookingService.getBookingById(5L)).thenReturn(null);

        assertThat(auth.isOwnerOrAdmin(5L, new AppPrincipal(1L, "u@test.it", "m.rossi", "Mario Rossi", "user"))).isTrue();
    }

    @Test
    void consenteAlProprietario() {
        when(bookingService.getBookingById(5L)).thenReturn(prenotazioneDi(1L));

        assertThat(auth.isOwnerOrAdmin(5L, new AppPrincipal(1L, "u@test.it", "m.rossi", "Mario Rossi", "user"))).isTrue();
    }

    @Test
    void deniesAnUnrelatedUser() {
        when(bookingService.getBookingById(5L)).thenReturn(prenotazioneDi(1L));

        assertThat(auth.isOwnerOrAdmin(5L, new AppPrincipal(99L, "altro@test.it", "m.rossi", "Mario Rossi", "user"))).isFalse();
    }

    @Test
    void allowsAnAdminOnSomeoneElsesBooking() {
        when(bookingService.getBookingById(5L)).thenReturn(prenotazioneDi(1L));

        assertThat(auth.isOwnerOrAdmin(5L, new AppPrincipal(2L, "admin@test.it", "m.rossi", "Mario Rossi", "admin"))).isTrue();
    }

    /** L'istantanea del proprietario, ora costruita a mano: la tabella utenti non e' piu' qui. */
    private static BookingOwner istantaneaDi(Long id, String username, String name) {
        return new BookingOwner(id, username, name);
    }
}
