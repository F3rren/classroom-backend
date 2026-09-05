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
import com.prenotazioni.model.Role;
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

    private LocalDateTime inizio;
    private LocalDateTime fine;

    @BeforeEach
    void setUp() {
        bookingRepository = mock(BookingRepository.class);
        roomRepository = mock(RoomRepository.class);
        courseRepository = mock(CourseRepository.class);
        service = new BookingService(bookingRepository, roomRepository, courseRepository);

        inizio = LocalDateTime.now().plusDays(1).withNano(0);
        fine = inizio.plusHours(2);
    }

    // ---------- helper ----------

    private Room room(Long id, RoomStatus status) {
        Room a = new Room();
        a.setId(id);
        a.setNome("Aula " + id);
        a.setStato(status);
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
        p.setAula(a);
        p.setUtente(u);
        p.setInizio(inizio);
        p.setFine(fine);
        p.setStato(status);
        return p;
    }

    /** Nessun conflitto: l'aula risulta libera per il periodo richiesto. */
    private void aulaLibera() {
        when(bookingRepository.findConflittingReservations(anyLong(), any(), any()))
                .thenReturn(List.of());
    }

    private void salvaComeArrivato() {
        when(bookingRepository.save(any(Booking.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    // ==================== prenotaAula ====================

    @Test
    void prenotaAulaRifiutaSeLAulaEOccupata() {
        when(bookingRepository.findConflittingReservations(anyLong(), any(), any()))
                .thenReturn(List.of(booking(1L, room(10L, RoomStatus.LIBERA), user(1L), BookingStatus.PRENOTATA)));

        assertThatThrownBy(() -> service.bookRoom(10L, null, user(1L), inizio, fine, "x"))
                .isInstanceOf(BookingConflictException.class);
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void prenotaAulaSegnalaAulaInesistente() {
        aulaLibera();
        when(roomRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.bookRoom(10L, null, user(1L), inizio, fine, "x"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // Il test "utente inesistente" e' stato rimosso con la separazione: questo servizio non
    // consulta piu' la tabella utenti, quindi non puo' piu' distinguere quel caso. A garantire
    // l'esistenza e' il token firmato da auth-service, entro la sua scadenza.

    @Test
    void prenotaAulaSegnalaCorsoInesistente() {
        aulaLibera();
        when(roomRepository.findById(10L)).thenReturn(Optional.of(room(10L, RoomStatus.LIBERA)));
        when(courseRepository.findById(77L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.bookRoom(10L, 77L, user(1L), inizio, fine, "x"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void prenotaAulaAttachesCourseWhenPresent() {
        aulaLibera();
        salvaComeArrivato();
        Course course = new Course();
        course.setId(77L);
        course.setNome("Analisi 1");
        when(roomRepository.findById(10L)).thenReturn(Optional.of(room(10L, RoomStatus.LIBERA)));
        when(courseRepository.findById(77L)).thenReturn(Optional.of(course));
        when(bookingRepository.findActiveReservations(anyLong(), any())).thenReturn(List.of());

        Booking creata = service.bookRoom(10L, 77L, user(1L), inizio, fine, "con corso");

        assertThat(creata).isNotNull();
        assertThat(creata.getCorso()).isSameAs(course);
        assertThat(creata.getStato()).isEqualTo(BookingStatus.PRENOTATA);
    }

    @Test
    void prenotaAulaLeavesRoomStateUnchangedWhenBookingIsInTheFuture() {
        aulaLibera();
        salvaComeArrivato();
        Room a = room(10L, RoomStatus.LIBERA);
        when(roomRepository.findById(10L)).thenReturn(Optional.of(a));
        // nessuna prenotazione attiva ADESSO -> lo stato resta "libera", nessun save sull'aula
        when(bookingRepository.findActiveReservations(anyLong(), any())).thenReturn(List.of());

        assertThat(service.bookRoom(10L, null, user(1L), inizio, fine, "x")).isNotNull();
        verify(roomRepository, never()).save(any());
    }

    @Test
    void prenotaAulaMarksRoomOccupiedWhenBookingIsActiveNow() {
        aulaLibera();
        salvaComeArrivato();
        Room a = room(10L, RoomStatus.LIBERA);
        when(roomRepository.findById(10L)).thenReturn(Optional.of(a));
        when(bookingRepository.findActiveReservations(anyLong(), any()))
                .thenReturn(List.of(booking(1L, a, user(1L), BookingStatus.PRENOTATA)));

        service.bookRoom(10L, null, user(1L), inizio, fine, "x");

        assertThat(a.getStato()).isEqualTo(RoomStatus.OCCUPATA);
        verify(roomRepository).save(a);
    }

    @Test
    void prenotaAulaMarksRoomInMaintenanceWhenAMaintenanceBookingIsActive() {
        aulaLibera();
        salvaComeArrivato();
        Room a = room(10L, RoomStatus.LIBERA);
        when(roomRepository.findById(10L)).thenReturn(Optional.of(a));
        when(bookingRepository.findActiveReservations(anyLong(), any()))
                .thenReturn(List.of(booking(1L, a, user(1L), BookingStatus.MANUTENZIONE)));

        service.bookRoom(10L, null, user(1L), inizio, fine, "x");

        assertThat(a.getStato()).isEqualTo(RoomStatus.MANUTENZIONE);
    }

    @Test
    void prenotaAulaMarksRoomBlockedWhenABlockingBookingIsActive() {
        aulaLibera();
        salvaComeArrivato();
        Room a = room(10L, RoomStatus.LIBERA);
        when(roomRepository.findById(10L)).thenReturn(Optional.of(a));
        when(bookingRepository.findActiveReservations(anyLong(), any()))
                .thenReturn(List.of(booking(1L, a, user(1L), BookingStatus.BLOCCATA)));

        service.bookRoom(10L, null, user(1L), inizio, fine, "x");

        assertThat(a.getStato()).isEqualTo(RoomStatus.BLOCCATA);
    }

    // ==================== bloccaAula ====================

    @Test
    void bloccaAulaRifiutaSeLAulaEOccupata() {
        when(bookingRepository.findConflittingReservations(anyLong(), any(), any()))
                .thenReturn(List.of(booking(1L, room(10L, RoomStatus.LIBERA), user(1L), BookingStatus.PRENOTATA)));

        assertThatThrownBy(() -> service.blockRoom(10L, user(2L), inizio, fine, "motivo"))
                .isInstanceOf(BookingConflictException.class);
    }

    @Test
    void bloccaAulaSegnalaAulaInesistente() {
        aulaLibera();
        when(roomRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.blockRoom(10L, user(2L), inizio, fine, "motivo"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // Rimosso: bloccaAula non rilegge piu' il ruolo dal database. A filtrare i non-admin
    // e' @PreAuthorize("hasRole('ADMIN')") sul controller, con il ruolo preso dal token;
    // verificarlo di nuovo qui richiederebbe una chiamata ad auth-service.

    @Test
    void bloccaAulaCreatesBlockedBookingForAdmin() {
        aulaLibera();
        salvaComeArrivato();
        when(roomRepository.findById(10L)).thenReturn(Optional.of(room(10L, RoomStatus.LIBERA)));

        Booking blocco = service.blockRoom(10L, user(2L), inizio, fine, "manutenzione straordinaria");

        assertThat(blocco).isNotNull();
        assertThat(blocco.getStato()).isEqualTo(BookingStatus.BLOCCATA);
        assertThat(blocco.getCorso()).isNull();
        assertThat(blocco.getDescrizione()).isEqualTo("manutenzione straordinaria");
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
        Room a = room(10L, RoomStatus.LIBERA);
        when(bookingRepository.findById(5L))
                .thenReturn(Optional.of(booking(5L, a, user(1L), BookingStatus.PRENOTATA)));

        assertThatThrownBy(() -> service.cancelBooking(5L, 9L, false))
                .isInstanceOf(AccessDeniedException.class);
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void annullaSucceedsForOwnerAndSetsStateToAnnullata() {
        Room a = room(10L, RoomStatus.OCCUPATA);
        Booking p = booking(5L, a, user(1L), BookingStatus.PRENOTATA);
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(p));
        when(roomRepository.findById(10L)).thenReturn(Optional.of(a));
        when(bookingRepository.findActiveReservations(anyLong(), any())).thenReturn(List.of());
        salvaComeArrivato();

        assertThat(service.cancelBooking(5L, 1L, false)).isTrue();
        assertThat(p.getStato()).isEqualTo(BookingStatus.ANNULLATA);
        // l'aula torna libera e viene salvata perche' lo stato e' cambiato
        assertThat(a.getStato()).isEqualTo(RoomStatus.LIBERA);
        verify(roomRepository).save(a);
    }

    @Test
    void annullaSucceedsForAdminOnSomeoneElsesBooking() {
        Room a = room(10L, RoomStatus.LIBERA);
        Booking p = booking(5L, a, user(1L), BookingStatus.PRENOTATA);
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(p));
        when(roomRepository.findById(10L)).thenReturn(Optional.of(a));
        when(bookingRepository.findActiveReservations(anyLong(), any())).thenReturn(List.of());
        salvaComeArrivato();

        assertThat(service.cancelBooking(5L, 2L, true)).isTrue();
    }

    @Test
    void annullaHandlesMissingRoomDuringStateRefresh() {
        // ramo "aula non trovata" dentro aggiornaStatoAula
        Room a = room(10L, RoomStatus.LIBERA);
        Booking p = booking(5L, a, user(1L), BookingStatus.PRENOTATA);
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(p));
        when(roomRepository.findById(10L)).thenReturn(Optional.empty());
        salvaComeArrivato();

        assertThat(service.cancelBooking(5L, 1L, false)).isTrue();
        verify(roomRepository, never()).save(any());
    }

    @Test
    void annullaRifiutaUnaPrenotazioneGiaAnnullata() {
        // Annullare due volte non deve riuscire: il chiamante riceverebbe un "annullata
        // con successo" per un'operazione che non ha cambiato nulla.
        Room a = room(10L, RoomStatus.LIBERA);
        Booking p = booking(5L, a, user(1L), BookingStatus.ANNULLATA);
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.cancelBooking(5L, 1L, false))
                .isInstanceOf(DomainConflictException.class);
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void annullaRifiutaUnBloccoAmministrativo() {
        // I blocchi e le manutenzioni sono roba da admin: si annullano dall'endpoint
        // admin dedicato, non da DELETE /api/prenotazioni/{id}.
        Room a = room(10L, RoomStatus.BLOCCATA);
        Booking p = booking(5L, a, user(1L), BookingStatus.BLOCCATA);
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.cancelBooking(5L, 1L, false))
                .isInstanceOf(DomainConflictException.class);
    }

    @Test
    void laRegolaSulloStatoValeAnchePerGliAdmin() {
        // La regola e' sullo stato, non sul ruolo: per annullare comunque una
        // prenotazione gia' annullata l'admin ha annullaPrenotazioneAsAdmin.
        Room a = room(10L, RoomStatus.LIBERA);
        Booking p = booking(5L, a, user(1L), BookingStatus.ANNULLATA);
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

        assertThatThrownBy(() -> service.updateBooking(5L, 10L, null, 1L, false, inizio, fine, "x"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // Rimosso: "utente inesistente" non e' piu' un caso che questo servizio possa
    // distinguere. Non consulta la tabella utenti dalla separazione di auth-service, e a
    // garantire l'esistenza e' il token firmato, entro la sua scadenza.

    @Test
    void updateRifiutaUnUtenteEstraneo() {
        when(bookingRepository.findById(5L)).thenReturn(
                Optional.of(booking(5L, room(10L, RoomStatus.LIBERA), user(1L), BookingStatus.PRENOTATA)));

        assertThatThrownBy(() -> service.updateBooking(5L, 10L, null, 9L, false, inizio, fine, "x"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void updateSegnalaAulaInesistente() {
        when(bookingRepository.findById(5L)).thenReturn(
                Optional.of(booking(5L, room(10L, RoomStatus.LIBERA), user(1L), BookingStatus.PRENOTATA)));
        when(roomRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateBooking(5L, 99L, null, 1L, false, inizio, fine, "x"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateRifiutaSeIlNuovoOrarioSiSovrappone() {
        Room a = room(10L, RoomStatus.LIBERA);
        when(bookingRepository.findById(5L)).thenReturn(
                Optional.of(booking(5L, a, user(1L), BookingStatus.PRENOTATA)));
        when(roomRepository.findById(10L)).thenReturn(Optional.of(a));
        when(bookingRepository.findConflittingReservationsExcluding(anyLong(), any(), any(), anyLong()))
                .thenReturn(List.of(booking(6L, a, user(2L), BookingStatus.PRENOTATA)));

        assertThatThrownBy(() -> service.updateBooking(5L, 10L, null, 1L, false, inizio, fine, "x"))
                .isInstanceOf(BookingConflictException.class);
    }

    @Test
    void updateSegnalaCorsoInesistente() {
        Room a = room(10L, RoomStatus.LIBERA);
        when(bookingRepository.findById(5L)).thenReturn(
                Optional.of(booking(5L, a, user(1L), BookingStatus.PRENOTATA)));
        when(roomRepository.findById(10L)).thenReturn(Optional.of(a));
        when(bookingRepository.findConflittingReservationsExcluding(anyLong(), any(), any(), anyLong()))
                .thenReturn(List.of());
        when(courseRepository.findById(77L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateBooking(5L, 10L, 77L, 1L, false, inizio, fine, "x"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void laModificaApplicaINuoviValoriPerIlProprietario() {
        Room vecchia = room(10L, RoomStatus.LIBERA);
        Room nuova = room(20L, RoomStatus.LIBERA);
        Booking p = booking(5L, vecchia, user(1L), BookingStatus.PRENOTATA);
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(p));
        when(roomRepository.findById(20L)).thenReturn(Optional.of(nuova));
        when(bookingRepository.findConflittingReservationsExcluding(anyLong(), any(), any(), anyLong()))
                .thenReturn(List.of());
        salvaComeArrivato();

        LocalDateTime nuovoInizio = inizio.plusDays(3);
        Booking aggiornata = service.updateBooking(
                5L, 20L, null, 1L, false, nuovoInizio, nuovoInizio.plusHours(1), "nuova descrizione");

        assertThat(aggiornata).isNotNull();
        assertThat(aggiornata.getAula()).isSameAs(nuova);
        assertThat(aggiornata.getInizio()).isEqualTo(nuovoInizio);
        assertThat(aggiornata.getDescrizione()).isEqualTo("nuova descrizione");
    }

    @Test
    void updateIsAllowedForAdminOnSomeoneElsesBooking() {
        Room a = room(10L, RoomStatus.LIBERA);
        when(bookingRepository.findById(5L)).thenReturn(
                Optional.of(booking(5L, a, user(1L), BookingStatus.PRENOTATA)));
        when(roomRepository.findById(10L)).thenReturn(Optional.of(a));
        when(bookingRepository.findConflittingReservationsExcluding(anyLong(), any(), any(), anyLong()))
                .thenReturn(List.of());
        salvaComeArrivato();

        assertThat(service.updateBooking(5L, 10L, null, 2L, true, inizio, fine, "x")).isNotNull();
    }

    // ==================== getStatoAula ====================

    @Test
    void statoAulaIsLiberaWithNoActiveBookings() {
        when(bookingRepository.findActiveReservations(anyLong(), any())).thenReturn(List.of());

        assertThat(service.getRoomStatus(10L, LocalDateTime.now())).isEqualTo("LIBERA");
    }

    @Test
    void statoAulaIsPrenotataWithAnOrdinaryBooking() {
        Room a = room(10L, RoomStatus.OCCUPATA);
        when(bookingRepository.findActiveReservations(anyLong(), any()))
                .thenReturn(List.of(booking(1L, a, user(1L), BookingStatus.PRENOTATA)));

        assertThat(service.getRoomStatus(10L, LocalDateTime.now())).isEqualTo("PRENOTATA");
    }

    @Test
    void statoAulaIsBloccataWhenABlockIsActive() {
        Room a = room(10L, RoomStatus.BLOCCATA);
        when(bookingRepository.findActiveReservations(anyLong(), any()))
                .thenReturn(List.of(
                        booking(1L, a, user(1L), BookingStatus.PRENOTATA),
                        booking(2L, a, user(2L), BookingStatus.BLOCCATA)));

        assertThat(service.getRoomStatus(10L, LocalDateTime.now())).isEqualTo("BLOCCATA");
    }

    @Test
    void manutenzioneWinsOverBloccata() {
        // priorita' dichiarata dal service: MANUTENZIONE > BLOCCATA > PRENOTATA
        Room a = room(10L, RoomStatus.MANUTENZIONE);
        when(bookingRepository.findActiveReservations(anyLong(), any()))
                .thenReturn(List.of(
                        booking(1L, a, user(2L), BookingStatus.BLOCCATA),
                        booking(2L, a, user(2L), BookingStatus.MANUTENZIONE)));

        assertThat(service.getRoomStatus(10L, LocalDateTime.now())).isEqualTo("MANUTENZIONE");
    }

}
