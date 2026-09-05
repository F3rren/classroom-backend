package com.prenotazioni.booking.service;

import com.prenotazioni.exception.BookingConflictException;
import com.prenotazioni.exception.DomainConflictException;
import com.prenotazioni.exception.ResourceNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import com.prenotazioni.booking.model.Room;
import com.prenotazioni.booking.model.RoomStatus;
import com.prenotazioni.booking.model.Course;
import com.prenotazioni.booking.model.Booking;
import com.prenotazioni.booking.model.BookingStatus;
import com.prenotazioni.booking.model.BookingOwner;
import com.prenotazioni.booking.repository.RoomRepository;
import com.prenotazioni.booking.repository.CourseRepository;
import com.prenotazioni.booking.repository.BookingRepository;
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
 * Unit test di PrenotazioneService con i 4 repository mockati.
 *
 * I test di integrazione esercitano solo il percorso felice: qui si coprono tutti i rami
 * di rifiuto (risorsa inesistente, permessi, slot occupato) che via HTTP sarebbero
 * scomodi o impossibili da provocare, e le transizioni di stato dell'aula.
 *
 * Package com.prenotazioni.service perche' il costruttore del service e' package-private.
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

    // ---------- helper ----------

    private Room room(Long id, RoomStatus status) {
        Room a = new Room();
        a.setId(id);
        a.setName("Aula " + id);
        a.setStatus(status);
        return a;
    }

    /**
     * L'istantanea del proprietario. Prima era un'entita' Utente con un ruolo: il ruolo
     * non serve piu' qui, perche' il servizio lo riceve dal chiamante come flag isAdmin
     * invece di rileggerlo dal database.
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

    /** Nessun conflitto: l'aula risulta libera per il periodo richiesto. */
    private void freeRoom() {
        when(bookingRepository.findConflictingBookings(anyLong(), any(), any()))
                .thenReturn(List.of());
    }

    private void saveAsGiven() {
        when(bookingRepository.save(any(Booking.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    // ==================== prenotaAula ====================

    @Test
    void prenotaAulaRifiutaSeLAulaEOccupata() {
        when(bookingRepository.findConflictingBookings(anyLong(), any(), any()))
                .thenReturn(List.of(booking(1L, room(10L, RoomStatus.FREE), user(1L), BookingStatus.BOOKED)));

        assertThatThrownBy(() -> service.bookRoom(10L, null, user(1L), startTime, endTime, "x"))
                .isInstanceOf(BookingConflictException.class);
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void prenotaAulaSegnalaAulaInesistente() {
        freeRoom();
        when(roomRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.bookRoom(10L, null, user(1L), startTime, endTime, "x"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // Il test "utente inesistente" e' stato rimosso con la separazione: questo servizio non
    // consulta piu' la tabella utenti, quindi non puo' piu' distinguere quel caso. A garantire
    // l'esistenza e' il token firmato da auth-service, entro la sua scadenza.

    @Test
    void prenotaAulaSegnalaCorsoInesistente() {
        freeRoom();
        when(roomRepository.findById(10L)).thenReturn(Optional.of(room(10L, RoomStatus.FREE)));
        when(courseRepository.findById(77L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.bookRoom(10L, 77L, user(1L), startTime, endTime, "x"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void prenotaAulaAttachesCourseWhenPresent() {
        freeRoom();
        saveAsGiven();
        Course course = new Course();
        course.setId(77L);
        course.setName("Analisi 1");
        when(roomRepository.findById(10L)).thenReturn(Optional.of(room(10L, RoomStatus.FREE)));
        when(courseRepository.findById(77L)).thenReturn(Optional.of(course));
        when(bookingRepository.findActiveBookings(anyLong(), any())).thenReturn(List.of());

        Booking creata = service.bookRoom(10L, 77L, user(1L), startTime, endTime, "con corso");

        assertThat(creata).isNotNull();
        assertThat(creata.getCourse()).isSameAs(course);
        assertThat(creata.getStatus()).isEqualTo(BookingStatus.BOOKED);
    }

    @Test
    void prenotaAulaLeavesRoomStateUnchangedWhenBookingIsInTheFuture() {
        freeRoom();
        saveAsGiven();
        Room a = room(10L, RoomStatus.FREE);
        when(roomRepository.findById(10L)).thenReturn(Optional.of(a));
        // nessuna prenotazione attiva ADESSO -> lo stato resta "free", nessun save sull'aula
        when(bookingRepository.findActiveBookings(anyLong(), any())).thenReturn(List.of());

        assertThat(service.bookRoom(10L, null, user(1L), startTime, endTime, "x")).isNotNull();
        verify(roomRepository, never()).save(any());
    }

    @Test
    void prenotaAulaMarksRoomOccupiedWhenBookingIsActiveNow() {
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
    void prenotaAulaMarksRoomInMaintenanceWhenAMaintenanceBookingIsActive() {
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
    void prenotaAulaMarksRoomBlockedWhenABlockingBookingIsActive() {
        freeRoom();
        saveAsGiven();
        Room a = room(10L, RoomStatus.FREE);
        when(roomRepository.findById(10L)).thenReturn(Optional.of(a));
        when(bookingRepository.findActiveBookings(anyLong(), any()))
                .thenReturn(List.of(booking(1L, a, user(1L), BookingStatus.BLOCKED)));

        service.bookRoom(10L, null, user(1L), startTime, endTime, "x");

        assertThat(a.getStatus()).isEqualTo(RoomStatus.BLOCKED);
    }

    // ==================== bloccaAula ====================

    @Test
    void bloccaAulaRifiutaSeLAulaEOccupata() {
        when(bookingRepository.findConflictingBookings(anyLong(), any(), any()))
                .thenReturn(List.of(booking(1L, room(10L, RoomStatus.FREE), user(1L), BookingStatus.BOOKED)));

        assertThatThrownBy(() -> service.blockRoom(10L, user(2L), startTime, endTime, "reason"))
                .isInstanceOf(BookingConflictException.class);
    }

    @Test
    void bloccaAulaSegnalaAulaInesistente() {
        freeRoom();
        when(roomRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.blockRoom(10L, user(2L), startTime, endTime, "reason"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // Rimosso: bloccaAula non rilegge piu' il ruolo dal database. A filtrare i non-admin
    // e' @PreAuthorize("hasRole('ADMIN')") sul controller, con il ruolo preso dal token;
    // verificarlo di nuovo qui richiederebbe una chiamata ad auth-service.

    @Test
    void bloccaAulaCreatesBlockedBookingForAdmin() {
        freeRoom();
        saveAsGiven();
        when(roomRepository.findById(10L)).thenReturn(Optional.of(room(10L, RoomStatus.FREE)));

        Booking blocco = service.blockRoom(10L, user(2L), startTime, endTime, "manutenzione straordinaria");

        assertThat(blocco).isNotNull();
        assertThat(blocco.getStatus()).isEqualTo(BookingStatus.BLOCKED);
        assertThat(blocco.getCourse()).isNull();
        assertThat(blocco.getDescription()).isEqualTo("manutenzione straordinaria");
    }

    // ==================== annullaPrenotazione ====================

    @Test
    void annullaSegnalaPrenotazioneInesistente() {
        when(bookingRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancelBooking(5L, 1L, false))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // Rimosso con la separazione: "utente inesistente" non e' piu' un caso che questo
    // servizio possa distinguere, perche' non consulta piu' la tabella utenti.

    @Test
    void annullaRifiutaUnUtenteEstraneo() {
        Room a = room(10L, RoomStatus.FREE);
        when(bookingRepository.findById(5L))
                .thenReturn(Optional.of(booking(5L, a, user(1L), BookingStatus.BOOKED)));

        assertThatThrownBy(() -> service.cancelBooking(5L, 9L, false))
                .isInstanceOf(AccessDeniedException.class);
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void annullaSucceedsForOwnerAndSetsStateToAnnullata() {
        Room a = room(10L, RoomStatus.BUSY);
        Booking p = booking(5L, a, user(1L), BookingStatus.BOOKED);
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(p));
        when(roomRepository.findById(10L)).thenReturn(Optional.of(a));
        when(bookingRepository.findActiveBookings(anyLong(), any())).thenReturn(List.of());
        saveAsGiven();

        assertThat(service.cancelBooking(5L, 1L, false)).isTrue();
        assertThat(p.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        // l'aula torna libera e viene salvata perche' lo stato e' cambiato
        assertThat(a.getStatus()).isEqualTo(RoomStatus.FREE);
        verify(roomRepository).save(a);
    }

    @Test
    void annullaSucceedsForAdminOnSomeoneElsesBooking() {
        Room a = room(10L, RoomStatus.FREE);
        Booking p = booking(5L, a, user(1L), BookingStatus.BOOKED);
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(p));
        when(roomRepository.findById(10L)).thenReturn(Optional.of(a));
        when(bookingRepository.findActiveBookings(anyLong(), any())).thenReturn(List.of());
        saveAsGiven();

        assertThat(service.cancelBooking(5L, 2L, true)).isTrue();
    }

    @Test
    void annullaHandlesMissingRoomDuringStateRefresh() {
        // ramo "aula non trovata" dentro aggiornaStatoAula
        Room a = room(10L, RoomStatus.FREE);
        Booking p = booking(5L, a, user(1L), BookingStatus.BOOKED);
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(p));
        when(roomRepository.findById(10L)).thenReturn(Optional.empty());
        saveAsGiven();

        assertThat(service.cancelBooking(5L, 1L, false)).isTrue();
        verify(roomRepository, never()).save(any());
    }

    @Test
    void annullaRifiutaUnaPrenotazioneGiaAnnullata() {
        // Annullare due volte non deve riuscire: il chiamante riceverebbe un "annullata
        // con successo" per un'operazione che non ha cambiato nulla.
        Room a = room(10L, RoomStatus.FREE);
        Booking p = booking(5L, a, user(1L), BookingStatus.CANCELLED);
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.cancelBooking(5L, 1L, false))
                .isInstanceOf(DomainConflictException.class);
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void annullaRifiutaUnBloccoAmministrativo() {
        // I blocchi e le manutenzioni sono roba da admin: si annullano dall'endpoint
        // admin dedicato, non da DELETE /api/bookings/{id}.
        Room a = room(10L, RoomStatus.BLOCKED);
        Booking p = booking(5L, a, user(1L), BookingStatus.BLOCKED);
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.cancelBooking(5L, 1L, false))
                .isInstanceOf(DomainConflictException.class);
    }

    @Test
    void laRegolaSulloStatoValeAnchePerGliAdmin() {
        // La regola e' sullo stato, non sul ruolo: per annullare comunque una
        // prenotazione gia' annullata l'admin ha annullaPrenotazioneAsAdmin.
        Room a = room(10L, RoomStatus.FREE);
        Booking p = booking(5L, a, user(1L), BookingStatus.CANCELLED);
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(p));

        // isAdmin=true e non false: prima era false, quindi a respingere era il controllo
        // di PROPRIETA', non la regola di stato che il test dice di verificare. Con i
        // booleani le due cose erano indistinguibili e il test passava lo stesso; con le
        // eccezioni tipizzate la differenza si vede, e il test ora prova cio' che dichiara.
        assertThatThrownBy(() -> service.cancelBooking(5L, 2L, true))
                .isInstanceOf(DomainConflictException.class);
    }

    // ==================== updatePrenotazione ====================

    @Test
    void updateSegnalaPrenotazioneInesistente() {
        when(bookingRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateBooking(5L, 10L, null, 1L, false, startTime, endTime, "x"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // Rimosso: "utente inesistente" non e' piu' un caso che questo servizio possa
    // distinguere. Non consulta la tabella utenti dalla separazione di auth-service, e a
    // garantire l'esistenza e' il token firmato, entro la sua scadenza.

    @Test
    void updateRifiutaUnUtenteEstraneo() {
        when(bookingRepository.findById(5L)).thenReturn(
                Optional.of(booking(5L, room(10L, RoomStatus.FREE), user(1L), BookingStatus.BOOKED)));

        assertThatThrownBy(() -> service.updateBooking(5L, 10L, null, 9L, false, startTime, endTime, "x"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void updateSegnalaAulaInesistente() {
        when(bookingRepository.findById(5L)).thenReturn(
                Optional.of(booking(5L, room(10L, RoomStatus.FREE), user(1L), BookingStatus.BOOKED)));
        when(roomRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateBooking(5L, 99L, null, 1L, false, startTime, endTime, "x"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateRifiutaSeIlNuovoOrarioSiSovrappone() {
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
    void updateSegnalaCorsoInesistente() {
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
    void laModificaApplicaINuoviValoriPerIlProprietario() {
        Room vecchia = room(10L, RoomStatus.FREE);
        Room nuova = room(20L, RoomStatus.FREE);
        Booking p = booking(5L, vecchia, user(1L), BookingStatus.BOOKED);
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(p));
        when(roomRepository.findById(20L)).thenReturn(Optional.of(nuova));
        when(bookingRepository.findConflictingBookingsExcluding(anyLong(), any(), any(), anyLong()))
                .thenReturn(List.of());
        saveAsGiven();

        LocalDateTime newStart = startTime.plusDays(3);
        Booking aggiornata = service.updateBooking(
                5L, 20L, null, 1L, false, newStart, newStart.plusHours(1), "nuova descrizione");

        assertThat(aggiornata).isNotNull();
        assertThat(aggiornata.getRoom()).isSameAs(nuova);
        assertThat(aggiornata.getStartTime()).isEqualTo(newStart);
        assertThat(aggiornata.getDescription()).isEqualTo("nuova descrizione");
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

    // ==================== getStatoAula ====================

    @Test
    void statoAulaIsLiberaWithNoActiveBookings() {
        when(bookingRepository.findActiveBookings(anyLong(), any())).thenReturn(List.of());

        assertThat(service.getRoomStatus(10L, LocalDateTime.now())).isEqualTo("FREE");
    }

    @Test
    void statoAulaIsPrenotataWithAnOrdinaryBooking() {
        Room a = room(10L, RoomStatus.BUSY);
        when(bookingRepository.findActiveBookings(anyLong(), any()))
                .thenReturn(List.of(booking(1L, a, user(1L), BookingStatus.BOOKED)));

        assertThat(service.getRoomStatus(10L, LocalDateTime.now())).isEqualTo("BOOKED");
    }

    @Test
    void statoAulaIsBloccataWhenABlockIsActive() {
        Room a = room(10L, RoomStatus.BLOCKED);
        when(bookingRepository.findActiveBookings(anyLong(), any()))
                .thenReturn(List.of(
                        booking(1L, a, user(1L), BookingStatus.BOOKED),
                        booking(2L, a, user(2L), BookingStatus.BLOCKED)));

        assertThat(service.getRoomStatus(10L, LocalDateTime.now())).isEqualTo("BLOCKED");
    }

    @Test
    void manutenzioneWinsOverBloccata() {
        // priorita' dichiarata dal service: MAINTENANCE > BLOCKED > BOOKED
        Room a = room(10L, RoomStatus.MAINTENANCE);
        when(bookingRepository.findActiveBookings(anyLong(), any()))
                .thenReturn(List.of(
                        booking(1L, a, user(2L), BookingStatus.BLOCKED),
                        booking(2L, a, user(2L), BookingStatus.MAINTENANCE)));

        assertThat(service.getRoomStatus(10L, LocalDateTime.now())).isEqualTo("MAINTENANCE");
    }

}
