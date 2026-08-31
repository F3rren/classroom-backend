package com.prenotazioni.service;

import com.prenotazioni.model.Aula;
import com.prenotazioni.model.Corso;
import com.prenotazioni.model.Prenotazione;
import com.prenotazioni.model.Utente;
import com.prenotazioni.repository.IAulaRepository;
import com.prenotazioni.repository.ICorsoRepository;
import com.prenotazioni.repository.IPrenotazioneRepository;
import com.prenotazioni.repository.IUtenteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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
class PrenotazioneServiceUnitTest {

    private IPrenotazioneRepository prenotazioneRepository;
    private IAulaRepository aulaRepository;
    private ICorsoRepository corsoRepository;
    private IUtenteRepository utenteRepository;
    private PrenotazioneService service;

    private LocalDateTime inizio;
    private LocalDateTime fine;

    @BeforeEach
    void setUp() {
        prenotazioneRepository = mock(IPrenotazioneRepository.class);
        aulaRepository = mock(IAulaRepository.class);
        corsoRepository = mock(ICorsoRepository.class);
        utenteRepository = mock(IUtenteRepository.class);
        service = new PrenotazioneService(prenotazioneRepository, aulaRepository, corsoRepository, utenteRepository);

        inizio = LocalDateTime.now().plusDays(1).withNano(0);
        fine = inizio.plusHours(2);
    }

    // ---------- helper ----------

    private Aula aula(Long id, String stato) {
        Aula a = new Aula();
        a.setId(id);
        a.setNome("Aula " + id);
        a.setStato(stato);
        return a;
    }

    private Utente utente(Long id, String ruolo) {
        Utente u = new Utente();
        u.setId(id);
        u.setNome("Utente " + id);
        u.setRuolo(ruolo);
        return u;
    }

    private Prenotazione prenotazione(Long id, Aula a, Utente u, String stato) {
        Prenotazione p = new Prenotazione();
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
        when(prenotazioneRepository.save(any(Prenotazione.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    // ==================== prenotaAula ====================

    @Test
    void prenotaAulaReturnsNullWhenRoomIsBusy() {
        when(prenotazioneRepository.findConflittingReservations(anyLong(), any(), any()))
                .thenReturn(List.of(prenotazione(1L, aula(10L, "libera"), utente(1L, "user"), "prenotata")));

        assertThat(service.prenotaAula(10L, null, 1L, inizio, fine, "x")).isNull();
        verify(prenotazioneRepository, never()).save(any());
    }

    @Test
    void prenotaAulaReturnsNullWhenRoomDoesNotExist() {
        aulaLibera();
        when(aulaRepository.findById(10L)).thenReturn(Optional.empty());
        when(utenteRepository.findById(1L)).thenReturn(Optional.of(utente(1L, "user")));

        assertThat(service.prenotaAula(10L, null, 1L, inizio, fine, "x")).isNull();
    }

    @Test
    void prenotaAulaReturnsNullWhenUserDoesNotExist() {
        aulaLibera();
        when(aulaRepository.findById(10L)).thenReturn(Optional.of(aula(10L, "libera")));
        when(utenteRepository.findById(1L)).thenReturn(Optional.empty());

        assertThat(service.prenotaAula(10L, null, 1L, inizio, fine, "x")).isNull();
    }

    @Test
    void prenotaAulaReturnsNullWhenCourseIdIsGivenButMissing() {
        aulaLibera();
        when(aulaRepository.findById(10L)).thenReturn(Optional.of(aula(10L, "libera")));
        when(utenteRepository.findById(1L)).thenReturn(Optional.of(utente(1L, "user")));
        when(corsoRepository.findById(77L)).thenReturn(Optional.empty());

        assertThat(service.prenotaAula(10L, 77L, 1L, inizio, fine, "x")).isNull();
    }

    @Test
    void prenotaAulaAttachesCourseWhenPresent() {
        aulaLibera();
        salvaComeArrivato();
        Corso corso = new Corso();
        corso.setId(77L);
        corso.setNome("Analisi 1");
        when(aulaRepository.findById(10L)).thenReturn(Optional.of(aula(10L, "libera")));
        when(utenteRepository.findById(1L)).thenReturn(Optional.of(utente(1L, "user")));
        when(corsoRepository.findById(77L)).thenReturn(Optional.of(corso));
        when(prenotazioneRepository.findActiveReservations(anyLong(), any())).thenReturn(List.of());

        Prenotazione creata = service.prenotaAula(10L, 77L, 1L, inizio, fine, "con corso");

        assertThat(creata).isNotNull();
        assertThat(creata.getCorso()).isSameAs(corso);
        assertThat(creata.getStato()).isEqualTo("prenotata");
    }

    @Test
    void prenotaAulaLeavesRoomStateUnchangedWhenBookingIsInTheFuture() {
        aulaLibera();
        salvaComeArrivato();
        Aula a = aula(10L, "libera");
        when(aulaRepository.findById(10L)).thenReturn(Optional.of(a));
        when(utenteRepository.findById(1L)).thenReturn(Optional.of(utente(1L, "user")));
        // nessuna prenotazione attiva ADESSO -> lo stato resta "libera", nessun save sull'aula
        when(prenotazioneRepository.findActiveReservations(anyLong(), any())).thenReturn(List.of());

        assertThat(service.prenotaAula(10L, null, 1L, inizio, fine, "x")).isNotNull();
        verify(aulaRepository, never()).save(any());
    }

    @Test
    void prenotaAulaMarksRoomOccupiedWhenBookingIsActiveNow() {
        aulaLibera();
        salvaComeArrivato();
        Aula a = aula(10L, "libera");
        when(aulaRepository.findById(10L)).thenReturn(Optional.of(a));
        when(utenteRepository.findById(1L)).thenReturn(Optional.of(utente(1L, "user")));
        when(prenotazioneRepository.findActiveReservations(anyLong(), any()))
                .thenReturn(List.of(prenotazione(1L, a, utente(1L, "user"), "prenotata")));

        service.prenotaAula(10L, null, 1L, inizio, fine, "x");

        assertThat(a.getStato()).isEqualTo("occupata");
        verify(aulaRepository).save(a);
    }

    @Test
    void prenotaAulaMarksRoomInMaintenanceWhenAMaintenanceBookingIsActive() {
        aulaLibera();
        salvaComeArrivato();
        Aula a = aula(10L, "libera");
        when(aulaRepository.findById(10L)).thenReturn(Optional.of(a));
        when(utenteRepository.findById(1L)).thenReturn(Optional.of(utente(1L, "user")));
        when(prenotazioneRepository.findActiveReservations(anyLong(), any()))
                .thenReturn(List.of(prenotazione(1L, a, utente(1L, "user"), "manutenzione")));

        service.prenotaAula(10L, null, 1L, inizio, fine, "x");

        assertThat(a.getStato()).isEqualTo("manutenzione");
    }

    @Test
    void prenotaAulaMarksRoomBlockedWhenABlockingBookingIsActive() {
        aulaLibera();
        salvaComeArrivato();
        Aula a = aula(10L, "libera");
        when(aulaRepository.findById(10L)).thenReturn(Optional.of(a));
        when(utenteRepository.findById(1L)).thenReturn(Optional.of(utente(1L, "user")));
        when(prenotazioneRepository.findActiveReservations(anyLong(), any()))
                .thenReturn(List.of(prenotazione(1L, a, utente(1L, "user"), "bloccata")));

        service.prenotaAula(10L, null, 1L, inizio, fine, "x");

        assertThat(a.getStato()).isEqualTo("bloccata");
    }

    // ==================== bloccaAula ====================

    @Test
    void bloccaAulaReturnsNullWhenRoomIsBusy() {
        when(prenotazioneRepository.findConflittingReservations(anyLong(), any(), any()))
                .thenReturn(List.of(prenotazione(1L, aula(10L, "libera"), utente(1L, "user"), "prenotata")));

        assertThat(service.bloccaAula(10L, 2L, inizio, fine, "motivo")).isNull();
    }

    @Test
    void bloccaAulaReturnsNullWhenRoomMissing() {
        aulaLibera();
        when(aulaRepository.findById(10L)).thenReturn(Optional.empty());
        when(utenteRepository.findById(2L)).thenReturn(Optional.of(utente(2L, "admin")));

        assertThat(service.bloccaAula(10L, 2L, inizio, fine, "motivo")).isNull();
    }

    @Test
    void bloccaAulaReturnsNullWhenActorIsNotAdmin() {
        aulaLibera();
        when(aulaRepository.findById(10L)).thenReturn(Optional.of(aula(10L, "libera")));
        when(utenteRepository.findById(3L)).thenReturn(Optional.of(utente(3L, "user")));

        assertThat(service.bloccaAula(10L, 3L, inizio, fine, "motivo")).isNull();
        verify(prenotazioneRepository, never()).save(any());
    }

    @Test
    void bloccaAulaCreatesBlockedBookingForAdmin() {
        aulaLibera();
        salvaComeArrivato();
        when(aulaRepository.findById(10L)).thenReturn(Optional.of(aula(10L, "libera")));
        when(utenteRepository.findById(2L)).thenReturn(Optional.of(utente(2L, "admin")));

        Prenotazione blocco = service.bloccaAula(10L, 2L, inizio, fine, "manutenzione straordinaria");

        assertThat(blocco).isNotNull();
        assertThat(blocco.getStato()).isEqualTo("bloccata");
        assertThat(blocco.getCorso()).isNull();
        assertThat(blocco.getDescrizione()).isEqualTo("manutenzione straordinaria");
    }

    // ==================== annullaPrenotazione ====================

    @Test
    void annullaReturnsFalseWhenBookingMissing() {
        when(prenotazioneRepository.findById(5L)).thenReturn(Optional.empty());

        assertThat(service.annullaPrenotazione(5L, 1L)).isFalse();
    }

    @Test
    void annullaReturnsFalseWhenUserMissing() {
        Aula a = aula(10L, "libera");
        when(prenotazioneRepository.findById(5L))
                .thenReturn(Optional.of(prenotazione(5L, a, utente(1L, "user"), "prenotata")));
        when(utenteRepository.findById(1L)).thenReturn(Optional.empty());

        assertThat(service.annullaPrenotazione(5L, 1L)).isFalse();
    }

    @Test
    void annullaReturnsFalseForUnrelatedUser() {
        Aula a = aula(10L, "libera");
        when(prenotazioneRepository.findById(5L))
                .thenReturn(Optional.of(prenotazione(5L, a, utente(1L, "user"), "prenotata")));
        when(utenteRepository.findById(9L)).thenReturn(Optional.of(utente(9L, "user")));

        assertThat(service.annullaPrenotazione(5L, 9L)).isFalse();
        verify(prenotazioneRepository, never()).save(any());
    }

    @Test
    void annullaSucceedsForOwnerAndSetsStateToAnnullata() {
        Aula a = aula(10L, "occupata");
        Prenotazione p = prenotazione(5L, a, utente(1L, "user"), "prenotata");
        when(prenotazioneRepository.findById(5L)).thenReturn(Optional.of(p));
        when(utenteRepository.findById(1L)).thenReturn(Optional.of(utente(1L, "user")));
        when(aulaRepository.findById(10L)).thenReturn(Optional.of(a));
        when(prenotazioneRepository.findActiveReservations(anyLong(), any())).thenReturn(List.of());
        salvaComeArrivato();

        assertThat(service.annullaPrenotazione(5L, 1L)).isTrue();
        assertThat(p.getStato()).isEqualTo("annullata");
        // l'aula torna libera e viene salvata perche' lo stato e' cambiato
        assertThat(a.getStato()).isEqualTo("libera");
        verify(aulaRepository).save(a);
    }

    @Test
    void annullaSucceedsForAdminOnSomeoneElsesBooking() {
        Aula a = aula(10L, "libera");
        Prenotazione p = prenotazione(5L, a, utente(1L, "user"), "prenotata");
        when(prenotazioneRepository.findById(5L)).thenReturn(Optional.of(p));
        when(utenteRepository.findById(2L)).thenReturn(Optional.of(utente(2L, "admin")));
        when(aulaRepository.findById(10L)).thenReturn(Optional.of(a));
        when(prenotazioneRepository.findActiveReservations(anyLong(), any())).thenReturn(List.of());
        salvaComeArrivato();

        assertThat(service.annullaPrenotazione(5L, 2L)).isTrue();
    }

    @Test
    void annullaHandlesMissingRoomDuringStateRefresh() {
        // ramo "aula non trovata" dentro aggiornaStatoAula
        Aula a = aula(10L, "libera");
        Prenotazione p = prenotazione(5L, a, utente(1L, "user"), "prenotata");
        when(prenotazioneRepository.findById(5L)).thenReturn(Optional.of(p));
        when(utenteRepository.findById(1L)).thenReturn(Optional.of(utente(1L, "user")));
        when(aulaRepository.findById(10L)).thenReturn(Optional.empty());
        salvaComeArrivato();

        assertThat(service.annullaPrenotazione(5L, 1L)).isTrue();
        verify(aulaRepository, never()).save(any());
    }

    // ==================== updatePrenotazione ====================

    @Test
    void updateReturnsNullWhenBookingMissing() {
        when(prenotazioneRepository.findById(5L)).thenReturn(Optional.empty());

        assertThat(service.updatePrenotazione(5L, 10L, null, 1L, inizio, fine, "x")).isNull();
    }

    @Test
    void updateReturnsNullWhenUserMissing() {
        when(prenotazioneRepository.findById(5L)).thenReturn(
                Optional.of(prenotazione(5L, aula(10L, "libera"), utente(1L, "user"), "prenotata")));
        when(utenteRepository.findById(1L)).thenReturn(Optional.empty());

        assertThat(service.updatePrenotazione(5L, 10L, null, 1L, inizio, fine, "x")).isNull();
    }

    @Test
    void updateReturnsNullForUnrelatedUser() {
        when(prenotazioneRepository.findById(5L)).thenReturn(
                Optional.of(prenotazione(5L, aula(10L, "libera"), utente(1L, "user"), "prenotata")));
        when(utenteRepository.findById(9L)).thenReturn(Optional.of(utente(9L, "user")));

        assertThat(service.updatePrenotazione(5L, 10L, null, 9L, inizio, fine, "x")).isNull();
    }

    @Test
    void updateReturnsNullWhenRoomMissing() {
        when(prenotazioneRepository.findById(5L)).thenReturn(
                Optional.of(prenotazione(5L, aula(10L, "libera"), utente(1L, "user"), "prenotata")));
        when(utenteRepository.findById(1L)).thenReturn(Optional.of(utente(1L, "user")));
        when(aulaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThat(service.updatePrenotazione(5L, 99L, null, 1L, inizio, fine, "x")).isNull();
    }

    @Test
    void updateReturnsNullWhenNewSlotOverlapsAnotherBooking() {
        Aula a = aula(10L, "libera");
        when(prenotazioneRepository.findById(5L)).thenReturn(
                Optional.of(prenotazione(5L, a, utente(1L, "user"), "prenotata")));
        when(utenteRepository.findById(1L)).thenReturn(Optional.of(utente(1L, "user")));
        when(aulaRepository.findById(10L)).thenReturn(Optional.of(a));
        when(prenotazioneRepository.findConflittingReservationsExcluding(anyLong(), any(), any(), anyLong()))
                .thenReturn(List.of(prenotazione(6L, a, utente(2L, "user"), "prenotata")));

        assertThat(service.updatePrenotazione(5L, 10L, null, 1L, inizio, fine, "x")).isNull();
    }

    @Test
    void updateReturnsNullWhenCourseIdIsGivenButMissing() {
        Aula a = aula(10L, "libera");
        when(prenotazioneRepository.findById(5L)).thenReturn(
                Optional.of(prenotazione(5L, a, utente(1L, "user"), "prenotata")));
        when(utenteRepository.findById(1L)).thenReturn(Optional.of(utente(1L, "user")));
        when(aulaRepository.findById(10L)).thenReturn(Optional.of(a));
        when(prenotazioneRepository.findConflittingReservationsExcluding(anyLong(), any(), any(), anyLong()))
                .thenReturn(List.of());
        when(corsoRepository.findById(77L)).thenReturn(Optional.empty());

        assertThat(service.updatePrenotazione(5L, 10L, 77L, 1L, inizio, fine, "x")).isNull();
    }

    @Test
    void updateAppliesNewValuesForOwner() {
        Aula vecchia = aula(10L, "libera");
        Aula nuova = aula(20L, "libera");
        Prenotazione p = prenotazione(5L, vecchia, utente(1L, "user"), "prenotata");
        when(prenotazioneRepository.findById(5L)).thenReturn(Optional.of(p));
        when(utenteRepository.findById(1L)).thenReturn(Optional.of(utente(1L, "user")));
        when(aulaRepository.findById(20L)).thenReturn(Optional.of(nuova));
        when(prenotazioneRepository.findConflittingReservationsExcluding(anyLong(), any(), any(), anyLong()))
                .thenReturn(List.of());
        salvaComeArrivato();

        LocalDateTime nuovoInizio = inizio.plusDays(3);
        Prenotazione aggiornata = service.updatePrenotazione(
                5L, 20L, null, 1L, nuovoInizio, nuovoInizio.plusHours(1), "nuova descrizione");

        assertThat(aggiornata).isNotNull();
        assertThat(aggiornata.getAula()).isSameAs(nuova);
        assertThat(aggiornata.getInizio()).isEqualTo(nuovoInizio);
        assertThat(aggiornata.getDescrizione()).isEqualTo("nuova descrizione");
    }

    @Test
    void updateIsAllowedForAdminOnSomeoneElsesBooking() {
        Aula a = aula(10L, "libera");
        when(prenotazioneRepository.findById(5L)).thenReturn(
                Optional.of(prenotazione(5L, a, utente(1L, "user"), "prenotata")));
        when(utenteRepository.findById(2L)).thenReturn(Optional.of(utente(2L, "admin")));
        when(aulaRepository.findById(10L)).thenReturn(Optional.of(a));
        when(prenotazioneRepository.findConflittingReservationsExcluding(anyLong(), any(), any(), anyLong()))
                .thenReturn(List.of());
        salvaComeArrivato();

        assertThat(service.updatePrenotazione(5L, 10L, null, 2L, inizio, fine, "x")).isNotNull();
    }

    // ==================== getStatoAula ====================

    @Test
    void statoAulaIsLiberaWithNoActiveBookings() {
        when(prenotazioneRepository.findActiveReservations(anyLong(), any())).thenReturn(List.of());

        assertThat(service.getStatoAula(10L, LocalDateTime.now())).isEqualTo("LIBERA");
    }

    @Test
    void statoAulaIsPrenotataWithAnOrdinaryBooking() {
        Aula a = aula(10L, "occupata");
        when(prenotazioneRepository.findActiveReservations(anyLong(), any()))
                .thenReturn(List.of(prenotazione(1L, a, utente(1L, "user"), "prenotata")));

        assertThat(service.getStatoAula(10L, LocalDateTime.now())).isEqualTo("PRENOTATA");
    }

    @Test
    void statoAulaIsBloccataWhenABlockIsActive() {
        Aula a = aula(10L, "bloccata");
        when(prenotazioneRepository.findActiveReservations(anyLong(), any()))
                .thenReturn(List.of(
                        prenotazione(1L, a, utente(1L, "user"), "prenotata"),
                        prenotazione(2L, a, utente(2L, "admin"), "bloccata")));

        assertThat(service.getStatoAula(10L, LocalDateTime.now())).isEqualTo("BLOCCATA");
    }

    @Test
    void manutenzioneWinsOverBloccata() {
        // priorita' dichiarata dal service: MANUTENZIONE > BLOCCATA > PRENOTATA
        Aula a = aula(10L, "manutenzione");
        when(prenotazioneRepository.findActiveReservations(anyLong(), any()))
                .thenReturn(List.of(
                        prenotazione(1L, a, utente(2L, "admin"), "bloccata"),
                        prenotazione(2L, a, utente(2L, "admin"), "manutenzione")));

        assertThat(service.getStatoAula(10L, LocalDateTime.now())).isEqualTo("MANUTENZIONE");
    }
}
