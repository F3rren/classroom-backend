package com.prenotazioni.prenotazione.service;

import com.prenotazioni.prenotazione.model.Aula;
import com.prenotazioni.prenotazione.model.StatoAula;
import com.prenotazioni.prenotazione.model.Corso;
import com.prenotazioni.prenotazione.model.Prenotazione;
import com.prenotazioni.prenotazione.model.StatoPrenotazione;
import com.prenotazioni.model.Ruolo;
import com.prenotazioni.prenotazione.model.ProprietarioPrenotazione;
import com.prenotazioni.prenotazione.repository.IAulaRepository;
import com.prenotazioni.prenotazione.repository.ICorsoRepository;
import com.prenotazioni.prenotazione.repository.IPrenotazioneRepository;
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
    private PrenotazioneService service;

    private LocalDateTime inizio;
    private LocalDateTime fine;

    @BeforeEach
    void setUp() {
        prenotazioneRepository = mock(IPrenotazioneRepository.class);
        aulaRepository = mock(IAulaRepository.class);
        corsoRepository = mock(ICorsoRepository.class);
        service = new PrenotazioneService(prenotazioneRepository, aulaRepository, corsoRepository);

        inizio = LocalDateTime.now().plusDays(1).withNano(0);
        fine = inizio.plusHours(2);
    }

    // ---------- helper ----------

    private Aula aula(Long id, StatoAula stato) {
        Aula a = new Aula();
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
    private ProprietarioPrenotazione utente(Long id) {
        return new ProprietarioPrenotazione(id, "utente" + id, "Utente " + id);
    }

    private Prenotazione prenotazione(Long id, Aula a, ProprietarioPrenotazione u, StatoPrenotazione stato) {
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
                .thenReturn(List.of(prenotazione(1L, aula(10L, StatoAula.LIBERA), utente(1L), StatoPrenotazione.PRENOTATA)));

        assertThat(service.prenotaAula(10L, null, utente(1L), inizio, fine, "x")).isNull();
        verify(prenotazioneRepository, never()).save(any());
    }

    @Test
    void prenotaAulaReturnsNullWhenRoomDoesNotExist() {
        aulaLibera();
        when(aulaRepository.findById(10L)).thenReturn(Optional.empty());

        assertThat(service.prenotaAula(10L, null, utente(1L), inizio, fine, "x")).isNull();
    }

    // Il test "utente inesistente" e' stato rimosso con la separazione: questo servizio non
    // consulta piu' la tabella utenti, quindi non puo' piu' distinguere quel caso. A garantire
    // l'esistenza e' il token firmato da auth-service, entro la sua scadenza.

    @Test
    void prenotaAulaReturnsNullWhenCourseIdIsGivenButMissing() {
        aulaLibera();
        when(aulaRepository.findById(10L)).thenReturn(Optional.of(aula(10L, StatoAula.LIBERA)));
        when(corsoRepository.findById(77L)).thenReturn(Optional.empty());

        assertThat(service.prenotaAula(10L, 77L, utente(1L), inizio, fine, "x")).isNull();
    }

    @Test
    void prenotaAulaAttachesCourseWhenPresent() {
        aulaLibera();
        salvaComeArrivato();
        Corso corso = new Corso();
        corso.setId(77L);
        corso.setNome("Analisi 1");
        when(aulaRepository.findById(10L)).thenReturn(Optional.of(aula(10L, StatoAula.LIBERA)));
        when(corsoRepository.findById(77L)).thenReturn(Optional.of(corso));
        when(prenotazioneRepository.findActiveReservations(anyLong(), any())).thenReturn(List.of());

        Prenotazione creata = service.prenotaAula(10L, 77L, utente(1L), inizio, fine, "con corso");

        assertThat(creata).isNotNull();
        assertThat(creata.getCorso()).isSameAs(corso);
        assertThat(creata.getStato()).isEqualTo(StatoPrenotazione.PRENOTATA);
    }

    @Test
    void prenotaAulaLeavesRoomStateUnchangedWhenBookingIsInTheFuture() {
        aulaLibera();
        salvaComeArrivato();
        Aula a = aula(10L, StatoAula.LIBERA);
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
        Aula a = aula(10L, StatoAula.LIBERA);
        when(aulaRepository.findById(10L)).thenReturn(Optional.of(a));
        when(prenotazioneRepository.findActiveReservations(anyLong(), any()))
                .thenReturn(List.of(prenotazione(1L, a, utente(1L), StatoPrenotazione.PRENOTATA)));

        service.prenotaAula(10L, null, utente(1L), inizio, fine, "x");

        assertThat(a.getStato()).isEqualTo(StatoAula.OCCUPATA);
        verify(aulaRepository).save(a);
    }

    @Test
    void prenotaAulaMarksRoomInMaintenanceWhenAMaintenanceBookingIsActive() {
        aulaLibera();
        salvaComeArrivato();
        Aula a = aula(10L, StatoAula.LIBERA);
        when(aulaRepository.findById(10L)).thenReturn(Optional.of(a));
        when(prenotazioneRepository.findActiveReservations(anyLong(), any()))
                .thenReturn(List.of(prenotazione(1L, a, utente(1L), StatoPrenotazione.MANUTENZIONE)));

        service.prenotaAula(10L, null, utente(1L), inizio, fine, "x");

        assertThat(a.getStato()).isEqualTo(StatoAula.MANUTENZIONE);
    }

    @Test
    void prenotaAulaMarksRoomBlockedWhenABlockingBookingIsActive() {
        aulaLibera();
        salvaComeArrivato();
        Aula a = aula(10L, StatoAula.LIBERA);
        when(aulaRepository.findById(10L)).thenReturn(Optional.of(a));
        when(prenotazioneRepository.findActiveReservations(anyLong(), any()))
                .thenReturn(List.of(prenotazione(1L, a, utente(1L), StatoPrenotazione.BLOCCATA)));

        service.prenotaAula(10L, null, utente(1L), inizio, fine, "x");

        assertThat(a.getStato()).isEqualTo(StatoAula.BLOCCATA);
    }

    // ==================== bloccaAula ====================

    @Test
    void bloccaAulaReturnsNullWhenRoomIsBusy() {
        when(prenotazioneRepository.findConflittingReservations(anyLong(), any(), any()))
                .thenReturn(List.of(prenotazione(1L, aula(10L, StatoAula.LIBERA), utente(1L), StatoPrenotazione.PRENOTATA)));

        assertThat(service.bloccaAula(10L, utente(2L), inizio, fine, "motivo")).isNull();
    }

    @Test
    void bloccaAulaReturnsNullWhenRoomMissing() {
        aulaLibera();
        when(aulaRepository.findById(10L)).thenReturn(Optional.empty());

        assertThat(service.bloccaAula(10L, utente(2L), inizio, fine, "motivo")).isNull();
    }

    // Rimosso: bloccaAula non rilegge piu' il ruolo dal database. A filtrare i non-admin
    // e' @PreAuthorize("hasRole('ADMIN')") sul controller, con il ruolo preso dal token;
    // verificarlo di nuovo qui richiederebbe una chiamata ad auth-service.

    @Test
    void bloccaAulaCreatesBlockedBookingForAdmin() {
        aulaLibera();
        salvaComeArrivato();
        when(aulaRepository.findById(10L)).thenReturn(Optional.of(aula(10L, StatoAula.LIBERA)));

        Prenotazione blocco = service.bloccaAula(10L, utente(2L), inizio, fine, "manutenzione straordinaria");

        assertThat(blocco).isNotNull();
        assertThat(blocco.getStato()).isEqualTo(StatoPrenotazione.BLOCCATA);
        assertThat(blocco.getCorso()).isNull();
        assertThat(blocco.getDescrizione()).isEqualTo("manutenzione straordinaria");
    }

    // ==================== annullaPrenotazione ====================

    @Test
    void annullaReturnsFalseWhenBookingMissing() {
        when(prenotazioneRepository.findById(5L)).thenReturn(Optional.empty());

        assertThat(service.annullaPrenotazione(5L, 1L, false)).isFalse();
    }

    // Rimosso con la separazione: "utente inesistente" non e' piu' un caso che questo
    // servizio possa distinguere, perche' non consulta piu' la tabella utenti.

    @Test
    void annullaReturnsFalseForUnrelatedUser() {
        Aula a = aula(10L, StatoAula.LIBERA);
        when(prenotazioneRepository.findById(5L))
                .thenReturn(Optional.of(prenotazione(5L, a, utente(1L), StatoPrenotazione.PRENOTATA)));

        assertThat(service.annullaPrenotazione(5L, 9L, false)).isFalse();
        verify(prenotazioneRepository, never()).save(any());
    }

    @Test
    void annullaSucceedsForOwnerAndSetsStateToAnnullata() {
        Aula a = aula(10L, StatoAula.OCCUPATA);
        Prenotazione p = prenotazione(5L, a, utente(1L), StatoPrenotazione.PRENOTATA);
        when(prenotazioneRepository.findById(5L)).thenReturn(Optional.of(p));
        when(aulaRepository.findById(10L)).thenReturn(Optional.of(a));
        when(prenotazioneRepository.findActiveReservations(anyLong(), any())).thenReturn(List.of());
        salvaComeArrivato();

        assertThat(service.annullaPrenotazione(5L, 1L, false)).isTrue();
        assertThat(p.getStato()).isEqualTo(StatoPrenotazione.ANNULLATA);
        // l'aula torna libera e viene salvata perche' lo stato e' cambiato
        assertThat(a.getStato()).isEqualTo(StatoAula.LIBERA);
        verify(aulaRepository).save(a);
    }

    @Test
    void annullaSucceedsForAdminOnSomeoneElsesBooking() {
        Aula a = aula(10L, StatoAula.LIBERA);
        Prenotazione p = prenotazione(5L, a, utente(1L), StatoPrenotazione.PRENOTATA);
        when(prenotazioneRepository.findById(5L)).thenReturn(Optional.of(p));
        when(aulaRepository.findById(10L)).thenReturn(Optional.of(a));
        when(prenotazioneRepository.findActiveReservations(anyLong(), any())).thenReturn(List.of());
        salvaComeArrivato();

        assertThat(service.annullaPrenotazione(5L, 2L, true)).isTrue();
    }

    @Test
    void annullaHandlesMissingRoomDuringStateRefresh() {
        // ramo "aula non trovata" dentro aggiornaStatoAula
        Aula a = aula(10L, StatoAula.LIBERA);
        Prenotazione p = prenotazione(5L, a, utente(1L), StatoPrenotazione.PRENOTATA);
        when(prenotazioneRepository.findById(5L)).thenReturn(Optional.of(p));
        when(aulaRepository.findById(10L)).thenReturn(Optional.empty());
        salvaComeArrivato();

        assertThat(service.annullaPrenotazione(5L, 1L, false)).isTrue();
        verify(aulaRepository, never()).save(any());
    }

    @Test
    void annullaReturnsFalseWhenBookingIsAlreadyCancelled() {
        // Annullare due volte non deve riuscire: il chiamante riceverebbe un "annullata
        // con successo" per un'operazione che non ha cambiato nulla.
        Aula a = aula(10L, StatoAula.LIBERA);
        Prenotazione p = prenotazione(5L, a, utente(1L), StatoPrenotazione.ANNULLATA);
        when(prenotazioneRepository.findById(5L)).thenReturn(Optional.of(p));

        assertThat(service.annullaPrenotazione(5L, 1L, false)).isFalse();
        verify(prenotazioneRepository, never()).save(any());
    }

    @Test
    void annullaReturnsFalseForABlockedBooking() {
        // I blocchi e le manutenzioni sono roba da admin: si annullano dall'endpoint
        // admin dedicato, non da DELETE /api/prenotazioni/{id}.
        Aula a = aula(10L, StatoAula.BLOCCATA);
        Prenotazione p = prenotazione(5L, a, utente(1L), StatoPrenotazione.BLOCCATA);
        when(prenotazioneRepository.findById(5L)).thenReturn(Optional.of(p));

        assertThat(service.annullaPrenotazione(5L, 1L, false)).isFalse();
    }

    @Test
    void theStateRuleAppliesToAdminsToo() {
        // La regola e' sullo stato, non sul ruolo: per annullare comunque una
        // prenotazione gia' annullata l'admin ha annullaPrenotazioneAsAdmin.
        Aula a = aula(10L, StatoAula.LIBERA);
        Prenotazione p = prenotazione(5L, a, utente(1L), StatoPrenotazione.ANNULLATA);
        when(prenotazioneRepository.findById(5L)).thenReturn(Optional.of(p));

        assertThat(service.annullaPrenotazione(5L, 2L, false)).isFalse();
    }

    // ==================== updatePrenotazione ====================

    @Test
    void updateReturnsNullWhenBookingMissing() {
        when(prenotazioneRepository.findById(5L)).thenReturn(Optional.empty());

        assertThat(service.updatePrenotazione(5L, 10L, null, 1L, false, inizio, fine, "x")).isNull();
    }

    @Test
    void updateReturnsNullWhenUserMissing() {
        when(prenotazioneRepository.findById(5L)).thenReturn(
                Optional.of(prenotazione(5L, aula(10L, StatoAula.LIBERA), utente(1L), StatoPrenotazione.PRENOTATA)));

        assertThat(service.updatePrenotazione(5L, 10L, null, 1L, false, inizio, fine, "x")).isNull();
    }

    @Test
    void updateReturnsNullForUnrelatedUser() {
        when(prenotazioneRepository.findById(5L)).thenReturn(
                Optional.of(prenotazione(5L, aula(10L, StatoAula.LIBERA), utente(1L), StatoPrenotazione.PRENOTATA)));

        assertThat(service.updatePrenotazione(5L, 10L, null, 9L, false, inizio, fine, "x")).isNull();
    }

    @Test
    void updateReturnsNullWhenRoomMissing() {
        when(prenotazioneRepository.findById(5L)).thenReturn(
                Optional.of(prenotazione(5L, aula(10L, StatoAula.LIBERA), utente(1L), StatoPrenotazione.PRENOTATA)));
        when(aulaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThat(service.updatePrenotazione(5L, 99L, null, 1L, false, inizio, fine, "x")).isNull();
    }

    @Test
    void updateReturnsNullWhenNewSlotOverlapsAnotherBooking() {
        Aula a = aula(10L, StatoAula.LIBERA);
        when(prenotazioneRepository.findById(5L)).thenReturn(
                Optional.of(prenotazione(5L, a, utente(1L), StatoPrenotazione.PRENOTATA)));
        when(aulaRepository.findById(10L)).thenReturn(Optional.of(a));
        when(prenotazioneRepository.findConflittingReservationsExcluding(anyLong(), any(), any(), anyLong()))
                .thenReturn(List.of(prenotazione(6L, a, utente(2L), StatoPrenotazione.PRENOTATA)));

        assertThat(service.updatePrenotazione(5L, 10L, null, 1L, false, inizio, fine, "x")).isNull();
    }

    @Test
    void updateReturnsNullWhenCourseIdIsGivenButMissing() {
        Aula a = aula(10L, StatoAula.LIBERA);
        when(prenotazioneRepository.findById(5L)).thenReturn(
                Optional.of(prenotazione(5L, a, utente(1L), StatoPrenotazione.PRENOTATA)));
        when(aulaRepository.findById(10L)).thenReturn(Optional.of(a));
        when(prenotazioneRepository.findConflittingReservationsExcluding(anyLong(), any(), any(), anyLong()))
                .thenReturn(List.of());
        when(corsoRepository.findById(77L)).thenReturn(Optional.empty());

        assertThat(service.updatePrenotazione(5L, 10L, 77L, 1L, false, inizio, fine, "x")).isNull();
    }

    @Test
    void updateAppliesNewValuesForOwner() {
        Aula vecchia = aula(10L, StatoAula.LIBERA);
        Aula nuova = aula(20L, StatoAula.LIBERA);
        Prenotazione p = prenotazione(5L, vecchia, utente(1L), StatoPrenotazione.PRENOTATA);
        when(prenotazioneRepository.findById(5L)).thenReturn(Optional.of(p));
        when(aulaRepository.findById(20L)).thenReturn(Optional.of(nuova));
        when(prenotazioneRepository.findConflittingReservationsExcluding(anyLong(), any(), any(), anyLong()))
                .thenReturn(List.of());
        salvaComeArrivato();

        LocalDateTime nuovoInizio = inizio.plusDays(3);
        Prenotazione aggiornata = service.updatePrenotazione(
                5L, 20L, null, 1L, false, nuovoInizio, nuovoInizio.plusHours(1), "nuova descrizione");

        assertThat(aggiornata).isNotNull();
        assertThat(aggiornata.getAula()).isSameAs(nuova);
        assertThat(aggiornata.getInizio()).isEqualTo(nuovoInizio);
        assertThat(aggiornata.getDescrizione()).isEqualTo("nuova descrizione");
    }

    @Test
    void updateIsAllowedForAdminOnSomeoneElsesBooking() {
        Aula a = aula(10L, StatoAula.LIBERA);
        when(prenotazioneRepository.findById(5L)).thenReturn(
                Optional.of(prenotazione(5L, a, utente(1L), StatoPrenotazione.PRENOTATA)));
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
        Aula a = aula(10L, StatoAula.OCCUPATA);
        when(prenotazioneRepository.findActiveReservations(anyLong(), any()))
                .thenReturn(List.of(prenotazione(1L, a, utente(1L), StatoPrenotazione.PRENOTATA)));

        assertThat(service.getStatoAula(10L, LocalDateTime.now())).isEqualTo("PRENOTATA");
    }

    @Test
    void statoAulaIsBloccataWhenABlockIsActive() {
        Aula a = aula(10L, StatoAula.BLOCCATA);
        when(prenotazioneRepository.findActiveReservations(anyLong(), any()))
                .thenReturn(List.of(
                        prenotazione(1L, a, utente(1L), StatoPrenotazione.PRENOTATA),
                        prenotazione(2L, a, utente(2L), StatoPrenotazione.BLOCCATA)));

        assertThat(service.getStatoAula(10L, LocalDateTime.now())).isEqualTo("BLOCCATA");
    }

    @Test
    void manutenzioneWinsOverBloccata() {
        // priorita' dichiarata dal service: MANUTENZIONE > BLOCCATA > PRENOTATA
        Aula a = aula(10L, StatoAula.MANUTENZIONE);
        when(prenotazioneRepository.findActiveReservations(anyLong(), any()))
                .thenReturn(List.of(
                        prenotazione(1L, a, utente(2L), StatoPrenotazione.BLOCCATA),
                        prenotazione(2L, a, utente(2L), StatoPrenotazione.MANUTENZIONE)));

        assertThat(service.getStatoAula(10L, LocalDateTime.now())).isEqualTo("MANUTENZIONE");
    }

}
