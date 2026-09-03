package com.prenotazioni.prenotazione.security;

import com.prenotazioni.prenotazione.model.Aula;
import com.prenotazioni.prenotazione.model.Prenotazione;
import com.prenotazioni.security.AppPrincipal;
import com.prenotazioni.prenotazione.model.ProprietarioPrenotazione;
import com.prenotazioni.prenotazione.service.PrenotazioneService;
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
class PrenotazioneAuthorizationServiceUnitTest {

    private PrenotazioneService prenotazioneService;
    private PrenotazioneAuthorizationService auth;

    @BeforeEach
    void setUp() {
        prenotazioneService = mock(PrenotazioneService.class);
        auth = new PrenotazioneAuthorizationService(prenotazioneService);
    }

    private Prenotazione prenotazioneDi(Long proprietarioId) {
        ProprietarioPrenotazione u = new ProprietarioPrenotazione(proprietarioId, "u" + proprietarioId, "Utente");
        Aula a = new Aula();
        a.setId(1L);
        Prenotazione p = new Prenotazione();
        p.setId(5L);
        p.setUtente(istantaneaDi(u.getId(), u.getUsername(), u.getNome()));
        p.setAula(a);
        return p;
    }

    @Test
    void deniesWhenThereIsNoPrincipal() {
        assertThat(auth.isOwnerOrAdmin(5L, null)).isFalse();
    }

    @Test
    void allowsWhenBookingDoesNotExistSoTheControllerCanReturn404() {
        // scelta deliberata: non si maschera un 404 con un 403
        when(prenotazioneService.getPrenotazioneById(5L)).thenReturn(null);

        assertThat(auth.isOwnerOrAdmin(5L, new AppPrincipal(1L, "u@test.it", "m.rossi", "Mario Rossi", "user"))).isTrue();
    }

    @Test
    void allowsTheOwner() {
        when(prenotazioneService.getPrenotazioneById(5L)).thenReturn(prenotazioneDi(1L));

        assertThat(auth.isOwnerOrAdmin(5L, new AppPrincipal(1L, "u@test.it", "m.rossi", "Mario Rossi", "user"))).isTrue();
    }

    @Test
    void deniesAnUnrelatedUser() {
        when(prenotazioneService.getPrenotazioneById(5L)).thenReturn(prenotazioneDi(1L));

        assertThat(auth.isOwnerOrAdmin(5L, new AppPrincipal(99L, "altro@test.it", "m.rossi", "Mario Rossi", "user"))).isFalse();
    }

    @Test
    void allowsAnAdminOnSomeoneElsesBooking() {
        when(prenotazioneService.getPrenotazioneById(5L)).thenReturn(prenotazioneDi(1L));

        assertThat(auth.isOwnerOrAdmin(5L, new AppPrincipal(2L, "admin@test.it", "m.rossi", "Mario Rossi", "admin"))).isTrue();
    }

    /** L'istantanea del proprietario, ora costruita a mano: la tabella utenti non e' piu' qui. */
    private static ProprietarioPrenotazione istantaneaDi(Long id, String username, String nome) {
        return new ProprietarioPrenotazione(id, username, nome);
    }
}
