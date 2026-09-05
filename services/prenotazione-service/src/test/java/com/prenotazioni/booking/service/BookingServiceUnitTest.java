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

    private BookingRepository prenotazioneRepository;
    private RoomRepository aulaRepository;
    private CourseRepository corsoRepository;
    private BookingService service;

    private LocalDateTime inizio;
    private LocalDateTime fine;

    @BeforeEach
    void setUp() {
        prenotazioneRepository = mock(BookingRepository.class);
        aulaRepository = mock(RoomRepository.class);
        corsoRepository = mock(CourseRepository.class);
        service = new BookingService(prenotazioneRepository, aulaRepository, corsoRepository);

        inizio = LocalDateTime.now().plusDays(1).withNano(0);
        fine = inizio.plusHours(2);
    }

    // ---------- helper ----------

    private Room aula(Long id, RoomStatus stato) {
        Room a = new Room();
        a.setId(id);
        a.setNome("Aula " + id);
        a.setStato(stato);
        return a;
    }

    /**
     * L'istantanea del proprietario. Prima era un'entita' Utente con un ruolo: il ruolo
     * non serve piu' qui, perche' il servizio lo riceve dal chiamante come flag isAdmin
     * invece di rileggerlo dal database.
     */
    private BookingOwner utente(Long id) {
        return new BookingOwner(id, "utente" + id, "Utente " + id);
    }

    private Booking prenotazione(Long id, Room a, BookingOwner u, BookingStatus stato) {
        Booking p = new Booking();
        p.setId(id);
        p.setAula(a);
        p.setUtente(u);
        p.setInizio(inizio);
        p.setFine(fine);
        p.setStato(stato);
        return p;
    }

    /** Nessun conflitto: l'aula risulta libera per il periodo richiesto. */
    private void aulaLibera() {
        when(prenotazioneRepository.findConflittingReservations(anyLong(), any(), any()))
                .thenReturn(List.of());
    }

    private void salvaComeArrivato() {
        when(prenotazioneRepository.save(any(Booking.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    // ==================== prenotaAula ====================

    @Test
    void prenotaAulaRifiutaSeLAulaEOccupata() {
        when(prenotazioneRepository.findConflittingReservations(anyLong(), any(), any()))
                .thenReturn(List.of(prenotazione(1L, aula(10L, RoomStatus.LIBERA), utente(1L), BookingStatus.PRENOTATA)));

        assertThatThrownBy(() -> service.prenotaAula(10L, null, utente(1L), inizio, fine, "x"))
                .isInstanceOf(BookingConflictException.class);
        verify(prenotazioneRepository, never()).save(any());
    }

    @Test
    void prenotaAulaSegnalaAulaInesistente() {
        aulaLibera();
        when(aulaRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.prenotaAula(10L, null, utente(1L), inizio, fine, "x"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // Il test "utente inesistente" e' stato rimosso con la separazione: questo servizio non
    // consulta piu' la tabella utenti, quindi non puo' piu' distinguere quel caso. A garantire
    // l'esistenza e' il token firmato da auth-service, entro la sua scadenza.

    @Test
    void prenotaAulaSegnalaCorsoInesistente() {
        aulaLibera();
        when(aulaRepository.findById(10L)).thenReturn(Optional.of(aula(10L, RoomStatus.LIBERA)));
        when(corsoRepository.findById(77L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.prenotaAula(10L, 77L, utente(1L), inizio, fine, "x"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void prenotaAulaAttachesCourseWhenPresent() {
        aulaLibera();
        salvaComeArrivato();
        Course corso = new Course();
        corso.setId(77L);
        corso.setNome("Analisi 1");
        when(aulaRepository.findById(10L)).thenReturn(Optional.of(aula(10L, RoomStatus.LIBERA)));
        when(corsoRepository.findById(77L)).thenReturn(Optional.of(corso));
        when(prenotazioneRepository.findActiveReservations(anyLong(), any())).thenReturn(List.of());

        Booking creata = service.prenotaAula(10L, 77L, utente(1L), inizio, fine, "con corso");

        assertThat(creata).isNotNull();
        assertThat(creata.getCorso()).isSameAs(corso);
        assertThat(creata.getStato()).isEqualTo(BookingStatus.PRENOTATA);
    }

    @Test
    void prenotaAulaLeavesRoomStateUnchangedWhenBookingIsInTheFuture() {
        aulaLibera();
        salvaComeArrivato();
        Room a = aula(10L, RoomStatus.LIBERA);
        when(aulaRepository.findById(10L)).thenReturn(Optional.of(a));
        // nessuna prenotazione attiva ADESSO -> lo stato resta "libera", nessun save sull'aula
        when(prenotazioneRepository.findActiveReservations(anyLong(), any())).thenReturn(List.of());

        assertThat(service.prenotaAula(10L, null, utente(1L), inizio, fine, "x")).isNotNull();
        verify(aulaRepository, never()).save(any());
    }

    @Test
    void prenotaAulaMarksRoomOccupiedWhenBookingIsActiveNow() {
        aulaLibera();
        salvaComeArrivato();
        Room a = aula(10L, RoomStatus.LIBERA);
        when(aulaRepository.findById(10L)).thenReturn(Optional.of(a));
        when(prenotazioneRepository.findActiveReservations(anyLong(), any()))
                .thenReturn(List.of(prenotazione(1L, a, utente(1L), BookingStatus.PRENOTATA)));

        service.prenotaAula(10L, null, utente(1L), inizio, fine, "x");

        assertThat(a.getStato()).isEqualTo(RoomStatus.OCCUPATA);
        verify(aulaRepository).save(a);
    }

    @Test
    void prenotaAulaMarksRoomInMaintenanceWhenAMaintenanceBookingIsActive() {
        aulaLibera();
        salvaComeArrivato();
        Room a = aula(10L, RoomStatus.LIBERA);
        when(aulaRepository.findById(10L)).thenReturn(Optional.of(a));
        when(prenotazioneRepository.findActiveReservations(anyLong(), any()))
                .thenReturn(List.of(prenotazione(1L, a, utente(1L), BookingStatus.MANUTENZIONE)));

        service.prenotaAula(10L, null, utente(1L), inizio, fine, "x");

        assertThat(a.getStato()).isEqualTo(RoomStatus.MANUTENZIONE);
    }

    @Test
    void prenotaAulaMarksRoomBlockedWhenABlockingBookingIsActive() {
        aulaLibera();
        salvaComeArrivato();
        Room a = aula(10L, RoomStatus.LIBERA);
        when(aulaRepository.findById(10L)).thenReturn(Optional.of(a));
        when(prenotazioneRepository.findActiveReservations(anyLong(), any()))
                .thenReturn(List.of(prenotazione(1L, a, utente(1L), BookingStatus.BLOCCATA)));

        service.prenotaAula(10L, null, utente(1L), inizio, fine, "x");

        assertThat(a.getStato()).isEqualTo(RoomStatus.BLOCCATA);
    }

    // ==================== bloccaAula ====================

    @Test
    void bloccaAulaRifiutaSeLAulaEOccupata() {
        when(prenotazioneRepository.findConflittingReservations(anyLong(), any(), any()))
                .thenReturn(List.of(prenotazione(1L, aula(10L, RoomStatus.LIBERA), utente(1L), BookingStatus.PRENOTATA)));

        assertThatThrownBy(() -> service.bloccaAula(10L, utente(2L), inizio, fine, "motivo"))
                .isInstanceOf(BookingConflictException.class);
    }

    @Test
    void bloccaAulaSegnalaAulaInesistente() {
        aulaLibera();
        when(aulaRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.bloccaAula(10L, utente(2L), inizio, fine, "motivo"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // Rimosso: bloccaAula non rilegge piu' il ruolo dal database. A filtrare i non-admin
    // e' @PreAuthorize("hasRole('ADMIN')") sul controller, con il ruolo preso dal token;
    // verificarlo di nuovo qui richiederebbe una chiamata ad auth-service.

    @Test
    void bloccaAulaCreatesBlockedBookingForAdmin() {
        aulaLibera();
        salvaComeArrivato();
        when(aulaRepository.findById(10L)).thenReturn(Optional.of(aula(10L, RoomStatus.LIBERA)));

        Booking blocco = service.bloccaAula(10L, utente(2L), inizio, fine, "manutenzione straordinaria");

        assertThat(blocco).isNotNull();
        assertThat(blocco.getStato()).isEqualTo(BookingStatus.BLOCCATA);
        assertThat(blocco.getCorso()).isNull();
        assertThat(blocco.getDescrizione()).isEqualTo("manutenzione straordinaria");
    }

    // ==================== annullaPrenotazione ====================

    @Test
    void annullaSegnalaPrenotazioneInesistente() {
        when(prenotazioneRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.annullaPrenotazione(5L, 1L, false))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // Rimosso con la separazione: "utente inesistente" non e' piu' un caso che questo
    // servizio possa distinguere, perche' non consulta piu' la tabella utenti.

    @Test
    void annullaRifiutaUnUtenteEstraneo() {
        Room a = aula(10L, RoomStatus.LIBERA);
        when(prenotazioneRepository.findById(5L))
                .thenReturn(Optional.of(prenotazione(5L, a, utente(1L), BookingStatus.PRENOTATA)));

        assertThatThrownBy(() -> service.annullaPrenotazione(5L, 9L, false))
                .isInstanceOf(AccessDeniedException.class);
        verify(prenotazioneRepository, never()).save(any());
    }

    @Test
    void annullaSucceedsForOwnerAndSetsStateToAnnullata() {
        Room a = aula(10L, RoomStatus.OCCUPATA);
        Booking p = prenotazione(5L, a, utente(1L), BookingStatus.PRENOTATA);
        when(prenotazioneRepository.findById(5L)).thenReturn(Optional.of(p));
        when(aulaRepository.findById(10L)).thenReturn(Optional.of(a));
        when(prenotazioneRepository.findActiveReservations(anyLong(), any())).thenReturn(List.of());
        salvaComeArrivato();

        assertThat(service.annullaPrenotazione(5L, 1L, false)).isTrue();
        assertThat(p.getStato()).isEqualTo(BookingStatus.ANNULLATA);
        // l'aula torna libera e viene salvata perche' lo stato e' cambiato
        assertThat(a.getStato()).isEqualTo(RoomStatus.LIBERA);
        verify(aulaRepository).save(a);
    }

    @Test
    void annullaSucceedsForAdminOnSomeoneElsesBooking() {
        Room a = aula(10L, RoomStatus.LIBERA);
        Booking p = prenotazione(5L, a, utente(1L), BookingStatus.PRENOTATA);
        when(prenotazioneRepository.findById(5L)).thenReturn(Optional.of(p));
        when(aulaRepository.findById(10L)).thenReturn(Optional.of(a));
        when(prenotazioneRepository.findActiveReservations(anyLong(), any())).thenReturn(List.of());
        salvaComeArrivato();

        assertThat(service.annullaPrenotazione(5L, 2L, true)).isTrue();
    }

    @Test
    void annullaHandlesMissingRoomDuringStateRefresh() {
        // ramo "aula non trovata" dentro aggiornaStatoAula
        Room a = aula(10L, RoomStatus.LIBERA);
        Booking p = prenotazione(5L, a, utente(1L), BookingStatus.PRENOTATA);
        when(prenotazioneRepository.findById(5L)).thenReturn(Optional.of(p));
        when(aulaRepository.findById(10L)).thenReturn(Optional.empty());
        salvaComeArrivato();

        assertThat(service.annullaPrenotazione(5L, 1L, false)).isTrue();
        verify(aulaRepository, never()).save(any());
    }

    @Test
    void annullaRifiutaUnaPrenotazioneGiaAnnullata() {
        // Annullare due volte non deve riuscire: il chiamante riceverebbe un "annullata
        // con successo" per un'operazione che non ha cambiato nulla.
        Room a = aula(10L, RoomStatus.LIBERA);
        Booking p = prenotazione(5L, a, utente(1L), BookingStatus.ANNULLATA);
        when(prenotazioneRepository.findById(5L)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.annullaPrenotazione(5L, 1L, false))
                .isInstanceOf(DomainConflictException.class);
        verify(prenotazioneRepository, never()).save(any());
    }

    @Test
    void annullaRifiutaUnBloccoAmministrativo() {
        // I blocchi e le manutenzioni sono roba da admin: si annullano dall'endpoint
        // admin dedicato, non da DELETE /api/prenotazioni/{id}.
        Room a = aula(10L, RoomStatus.BLOCCATA);
        Booking p = prenotazione(5L, a, utente(1L), BookingStatus.BLOCCATA);
        when(prenotazioneRepository.findById(5L)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.annullaPrenotazione(5L, 1L, false))
                .isInstanceOf(DomainConflictException.class);
    }

    @Test
    void laRegolaSulloStatoValeAnchePerGliAdmin() {
        // La regola e' sullo stato, non sul ruolo: per annullare comunque una
        // prenotazione gia' annullata l'admin ha annullaPrenotazioneAsAdmin.
        Room a = aula(10L, RoomStatus.LIBERA);
        Booking p = prenotazione(5L, a, utente(1L), BookingStatus.ANNULLATA);
        when(prenotazioneRepository.findById(5L)).thenReturn(Optional.of(p));

        // isAdmin=true e non false: prima era false, quindi a respingere era il controllo
        // di PROPRIETA', non la regola di stato che il test dice di verificare. Con i
        // booleani le due cose erano indistinguibili e il test passava lo stesso; con le
        // eccezioni tipizzate la differenza si vede, e il test ora prova cio' che dichiara.
        assertThatThrownBy(() -> service.annullaPrenotazione(5L, 2L, true))
                .isInstanceOf(DomainConflictException.class);
    }

    // ==================== updatePrenotazione ====================

    @Test
    void updateSegnalaPrenotazioneInesistente() {
        when(prenotazioneRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updatePrenotazione(5L, 10L, null, 1L, false, inizio, fine, "x"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // Rimosso: "utente inesistente" non e' piu' un caso che questo servizio possa
    // distinguere. Non consulta la tabella utenti dalla separazione di auth-service, e a
    // garantire l'esistenza e' il token firmato, entro la sua scadenza.

    @Test
    void updateRifiutaUnUtenteEstraneo() {
        when(prenotazioneRepository.findById(5L)).thenReturn(
                Optional.of(prenotazione(5L, aula(10L, RoomStatus.LIBERA), utente(1L), BookingStatus.PRENOTATA)));

        assertThatThrownBy(() -> service.updatePrenotazione(5L, 10L, null, 9L, false, inizio, fine, "x"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void updateSegnalaAulaInesistente() {
        when(prenotazioneRepository.findById(5L)).thenReturn(
                Optional.of(prenotazione(5L, aula(10L, RoomStatus.LIBERA), utente(1L), BookingStatus.PRENOTATA)));
        when(aulaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updatePrenotazione(5L, 99L, null, 1L, false, inizio, fine, "x"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateRifiutaSeIlNuovoOrarioSiSovrappone() {
        Room a = aula(10L, RoomStatus.LIBERA);
        when(prenotazioneRepository.findById(5L)).thenReturn(
                Optional.of(prenotazione(5L, a, utente(1L), BookingStatus.PRENOTATA)));
        when(aulaRepository.findById(10L)).thenReturn(Optional.of(a));
        when(prenotazioneRepository.findConflittingReservationsExcluding(anyLong(), any(), any(), anyLong()))
                .thenReturn(List.of(prenotazione(6L, a, utente(2L), BookingStatus.PRENOTATA)));

        assertThatThrownBy(() -> service.updatePrenotazione(5L, 10L, null, 1L, false, inizio, fine, "x"))
                .isInstanceOf(BookingConflictException.class);
    }

    @Test
    void updateSegnalaCorsoInesistente() {
        Room a = aula(10L, RoomStatus.LIBERA);
        when(prenotazioneRepository.findById(5L)).thenReturn(
                Optional.of(prenotazione(5L, a, utente(1L), BookingStatus.PRENOTATA)));
        when(aulaRepository.findById(10L)).thenReturn(Optional.of(a));
        when(prenotazioneRepository.findConflittingReservationsExcluding(anyLong(), any(), any(), anyLong()))
                .thenReturn(List.of());
        when(corsoRepository.findById(77L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updatePrenotazione(5L, 10L, 77L, 1L, false, inizio, fine, "x"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void laModificaApplicaINuoviValoriPerIlProprietario() {
        Room vecchia = aula(10L, RoomStatus.LIBERA);
        Room nuova = aula(20L, RoomStatus.LIBERA);
        Booking p = prenotazione(5L, vecchia, utente(1L), BookingStatus.PRENOTATA);
        when(prenotazioneRepository.findById(5L)).thenReturn(Optional.of(p));
        when(aulaRepository.findById(20L)).thenReturn(Optional.of(nuova));
        when(prenotazioneRepository.findConflittingReservationsExcluding(anyLong(), any(), any(), anyLong()))
                .thenReturn(List.of());
        salvaComeArrivato();

        LocalDateTime nuovoInizio = inizio.plusDays(3);
        Booking aggiornata = service.updatePrenotazione(
                5L, 20L, null, 1L, false, nuovoInizio, nuovoInizio.plusHours(1), "nuova descrizione");

        assertThat(aggiornata).isNotNull();
        assertThat(aggiornata.getAula()).isSameAs(nuova);
        assertThat(aggiornata.getInizio()).isEqualTo(nuovoInizio);
        assertThat(aggiornata.getDescrizione()).isEqualTo("nuova descrizione");
    }

    @Test
    void updateIsAllowedForAdminOnSomeoneElsesBooking() {
        Room a = aula(10L, RoomStatus.LIBERA);
        when(prenotazioneRepository.findById(5L)).thenReturn(
                Optional.of(prenotazione(5L, a, utente(1L), BookingStatus.PRENOTATA)));
        when(aulaRepository.findById(10L)).thenReturn(Optional.of(a));
        when(prenotazioneRepository.findConflittingReservationsExcluding(anyLong(), any(), any(), anyLong()))
                .thenReturn(List.of());
        salvaComeArrivato();

        assertThat(service.updatePrenotazione(5L, 10L, null, 2L, true, inizio, fine, "x")).isNotNull();
    }

    // ==================== getStatoAula ====================

    @Test
    void statoAulaIsLiberaWithNoActiveBookings() {
        when(prenotazioneRepository.findActiveReservations(anyLong(), any())).thenReturn(List.of());

        assertThat(service.getStatoAula(10L, LocalDateTime.now())).isEqualTo("LIBERA");
    }

    @Test
    void statoAulaIsPrenotataWithAnOrdinaryBooking() {
        Room a = aula(10L, RoomStatus.OCCUPATA);
        when(prenotazioneRepository.findActiveReservations(anyLong(), any()))
                .thenReturn(List.of(prenotazione(1L, a, utente(1L), BookingStatus.PRENOTATA)));

        assertThat(service.getStatoAula(10L, LocalDateTime.now())).isEqualTo("PRENOTATA");
    }

    @Test
    void statoAulaIsBloccataWhenABlockIsActive() {
        Room a = aula(10L, RoomStatus.BLOCCATA);
        when(prenotazioneRepository.findActiveReservations(anyLong(), any()))
                .thenReturn(List.of(
                        prenotazione(1L, a, utente(1L), BookingStatus.PRENOTATA),
                        prenotazione(2L, a, utente(2L), BookingStatus.BLOCCATA)));

        assertThat(service.getStatoAula(10L, LocalDateTime.now())).isEqualTo("BLOCCATA");
    }

    @Test
    void manutenzioneWinsOverBloccata() {
        // priorita' dichiarata dal service: MANUTENZIONE > BLOCCATA > PRENOTATA
        Room a = aula(10L, RoomStatus.MANUTENZIONE);
        when(prenotazioneRepository.findActiveReservations(anyLong(), any()))
                .thenReturn(List.of(
                        prenotazione(1L, a, utente(2L), BookingStatus.BLOCCATA),
                        prenotazione(2L, a, utente(2L), BookingStatus.MANUTENZIONE)));

        assertThat(service.getStatoAula(10L, LocalDateTime.now())).isEqualTo("MANUTENZIONE");
    }

}
