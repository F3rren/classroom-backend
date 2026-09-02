package com.prenotazioni.controller;

import com.prenotazioni.dto.ApiEnvelope;
import com.prenotazioni.dto.PrenotazioneRequest;
import com.prenotazioni.exception.BookingConflictException;
import com.prenotazioni.model.Aula;
import com.prenotazioni.model.Prenotazione;
import com.prenotazioni.model.StatoPrenotazione;
import com.prenotazioni.model.ProprietarioPrenotazione;
import com.prenotazioni.security.AppPrincipal;
import com.prenotazioni.service.PrenotazioneService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test (niente contesto Spring) di PrenotazioneController.
 *
 * Copre i rami NON raggiungibili via HTTP dai test di integrazione:
 *  - i tre catch di DataIntegrityViolationException: su H2 il vincolo anti-sovrapposizione
 *    "EXCLUDE USING gist" non esiste (e' solo su Postgres, e i test usano ddl-auto=create-drop,
 *    quindi lo schema nasce dalle entity), percio' quell'eccezione non puo' mai scattare via HTTP;
 *  - i rami di parsing e di range delle date, che stanno sotto la soglia di Bean Validation.
 *
 * Vive in com.prenotazioni.controller e non nella root come gli altri test, perche' il
 * costruttore del controller e' package-private.
 */
class PrenotazioneControllerUnitTest {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private PrenotazioneService service;
    private PrenotazioneController controller;
    private AppPrincipal utente;
    private AppPrincipal admin;

    @BeforeEach
    void setUp() {
        service = mock(PrenotazioneService.class);
        controller = new PrenotazioneController(service);
        utente = new AppPrincipal(1L, "user@test.it", "m.rossi", "Mario Rossi", "user");
        admin = new AppPrincipal(2L, "admin@test.it", "m.rossi", "Mario Rossi", "admin");
    }

    // ---------- helper ----------

    private PrenotazioneRequest richiesta(String inizio, String fine) {
        PrenotazioneRequest r = new PrenotazioneRequest();
        r.setAulaId(10L);
        r.setCorsoId(null);
        r.setInizio(inizio);
        r.setFine(fine);
        r.setDescrizione("descrizione di test");
        return r;
    }

    private PrenotazioneRequest richiestaValida() {
        LocalDateTime inizio = LocalDateTime.now().plusDays(1).withNano(0);
        return richiesta(inizio.format(ISO), inizio.plusHours(2).format(ISO));
    }

    private Prenotazione prenotazioneFinta() {
        Aula aula = new Aula();
        aula.setId(10L);
        aula.setNome("Aula Finta");
        ProprietarioPrenotazione u = new ProprietarioPrenotazione(1L, "utente", "Utente Test");
        Prenotazione p = new Prenotazione();
        p.setId(99L);
        p.setAula(aula);
        p.setUtente(istantaneaDi(u.getId(), u.getUsername(), u.getNome()));
        p.setInizio(LocalDateTime.now().plusDays(1));
        p.setFine(LocalDateTime.now().plusDays(1).plusHours(2));
        p.setStato(StatoPrenotazione.PRENOTATA);
        return p;
    }

    @SuppressWarnings("unchecked")
    private String errorCode(ResponseEntity<?> resp) {
        return ((ApiEnvelope<Object>) resp.getBody()).getError();
    }

    // ==================== prenotaAula ====================

    @Test
    void prenotaAulaRejectsUnparsableStartDate() {
        ResponseEntity<?> resp = controller.prenotaAula(richiesta("non-una-data", "2030-01-01T12:00:00"), utente);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(errorCode(resp)).isEqualTo("INVALID_START_DATE");
    }

    @Test
    void prenotaAulaRejectsUnparsableEndDate() {
        ResponseEntity<?> resp = controller.prenotaAula(richiesta("2030-01-01T10:00:00", "non-una-data"), utente);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(errorCode(resp)).isEqualTo("INVALID_END_DATE");
    }

    @Test
    void prenotaAulaRejectsEndBeforeStart() {
        ResponseEntity<?> resp = controller.prenotaAula(
                richiesta("2030-01-01T12:00:00", "2030-01-01T10:00:00"), utente);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(errorCode(resp)).isEqualTo("INVALID_DATE_RANGE");
    }

    @Test
    void prenotaAulaRejectsDateInThePast() {
        LocalDateTime passato = LocalDateTime.now().minusDays(2).withNano(0);
        ResponseEntity<?> resp = controller.prenotaAula(
                richiesta(passato.format(ISO), passato.plusHours(1).format(ISO)), utente);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(errorCode(resp)).isEqualTo("PAST_DATE");
    }

    @Test
    void prenotaAulaReturns409WhenServiceRefuses() {
        when(service.prenotaAula(anyLong(), any(), any(), any(), any(), anyString())).thenReturn(null);

        ResponseEntity<?> resp = controller.prenotaAula(richiestaValida(), utente);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(errorCode(resp)).isEqualTo("BOOKING_CONFLICT");
    }

    @Test
    void prenotaAulaTranslatesDbConstraintIntoBookingConflict() {
        when(service.prenotaAula(anyLong(), any(), any(), any(), any(), anyString()))
                .thenThrow(new DataIntegrityViolationException("prenotazioni_no_overlap"));

        PrenotazioneRequest req = richiestaValida();
        assertThatThrownBy(() -> controller.prenotaAula(req, utente))
                .isInstanceOf(BookingConflictException.class)
                .satisfies(e -> assertThat(((BookingConflictException) e).getErrorCode()).isEqualTo("BOOKING_CONFLICT"));
    }

    @Test
    void prenotaAulaReturns201OnSuccess() {
        when(service.prenotaAula(anyLong(), any(), any(), any(), any(), anyString()))
                .thenReturn(prenotazioneFinta());

        ResponseEntity<?> resp = controller.prenotaAula(richiestaValida(), utente);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    // ==================== modificaPrenotazione (PUT) ====================

    @Test
    void modificaRejectsUnparsableStartDate() {
        ResponseEntity<?> resp = controller.modificaPrenotazione(
                5L, richiesta("boom", "2030-01-01T12:00:00"), utente);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(errorCode(resp)).isEqualTo("INVALID_START_DATE");
    }

    @Test
    void modificaRejectsUnparsableEndDate() {
        ResponseEntity<?> resp = controller.modificaPrenotazione(
                5L, richiesta("2030-01-01T10:00:00", "boom"), utente);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(errorCode(resp)).isEqualTo("INVALID_END_DATE");
    }

    @Test
    void modificaRejectsEndBeforeStart() {
        ResponseEntity<?> resp = controller.modificaPrenotazione(
                5L, richiesta("2030-01-01T12:00:00", "2030-01-01T10:00:00"), utente);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(errorCode(resp)).isEqualTo("INVALID_DATE_RANGE");
    }

    @Test
    void modificaRejectsDateInThePast() {
        LocalDateTime passato = LocalDateTime.now().minusDays(2).withNano(0);
        ResponseEntity<?> resp = controller.modificaPrenotazione(
                5L, richiesta(passato.format(ISO), passato.plusHours(1).format(ISO)), utente);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(errorCode(resp)).isEqualTo("PAST_DATE");
    }

    @Test
    void modificaReturns409WhenServiceRefuses() {
        when(service.updatePrenotazione(anyLong(), anyLong(), any(), anyLong(), anyBoolean(), any(), any(), anyString()))
                .thenReturn(null);

        ResponseEntity<?> resp = controller.modificaPrenotazione(5L, richiestaValida(), utente);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(errorCode(resp)).isEqualTo("UPDATE_FAILED");
    }

    @Test
    void modificaTranslatesDbConstraintIntoUpdateConflict() {
        when(service.updatePrenotazione(anyLong(), anyLong(), any(), anyLong(), anyBoolean(), any(), any(), anyString()))
                .thenThrow(new DataIntegrityViolationException("prenotazioni_no_overlap"));

        PrenotazioneRequest req = richiestaValida();
        assertThatThrownBy(() -> controller.modificaPrenotazione(5L, req, utente))
                .isInstanceOf(BookingConflictException.class)
                .satisfies(e -> assertThat(((BookingConflictException) e).getErrorCode()).isEqualTo("UPDATE_CONFLICT"));
    }

    @Test
    void modificaReturns200OnSuccess() {
        when(service.updatePrenotazione(anyLong(), anyLong(), any(), anyLong(), anyBoolean(), any(), any(), anyString()))
                .thenReturn(prenotazioneFinta());

        ResponseEntity<?> resp = controller.modificaPrenotazione(5L, richiestaValida(), utente);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ==================== bloccaAula ====================

    @Test
    void bloccaRejectsUnparsableStartDate() {
        ResponseEntity<?> resp = controller.bloccaAula(richiesta("boom", "2030-01-01T12:00:00"), admin);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(errorCode(resp)).isEqualTo("INVALID_START_DATE");
    }

    @Test
    void bloccaRejectsUnparsableEndDate() {
        ResponseEntity<?> resp = controller.bloccaAula(richiesta("2030-01-01T10:00:00", "boom"), admin);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(errorCode(resp)).isEqualTo("INVALID_END_DATE");
    }

    @Test
    void bloccaRejectsEndBeforeStart() {
        ResponseEntity<?> resp = controller.bloccaAula(
                richiesta("2030-01-01T12:00:00", "2030-01-01T10:00:00"), admin);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(errorCode(resp)).isEqualTo("INVALID_DATE_RANGE");
    }

    @Test
    void bloccaReturns409WhenServiceRefuses() {
        when(service.bloccaAula(anyLong(), any(), any(), any(), anyString())).thenReturn(null);

        ResponseEntity<?> resp = controller.bloccaAula(richiestaValida(), admin);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(errorCode(resp)).isEqualTo("BLOCK_CONFLICT");
    }

    @Test
    void bloccaTranslatesDbConstraintIntoBlockConflict() {
        when(service.bloccaAula(anyLong(), any(), any(), any(), anyString()))
                .thenThrow(new DataIntegrityViolationException("prenotazioni_no_overlap"));

        PrenotazioneRequest req = richiestaValida();
        assertThatThrownBy(() -> controller.bloccaAula(req, admin))
                .isInstanceOf(BookingConflictException.class)
                .satisfies(e -> assertThat(((BookingConflictException) e).getErrorCode()).isEqualTo("BLOCK_CONFLICT"));
    }

    @Test
    void bloccaReturns201OnSuccess() {
        when(service.bloccaAula(anyLong(), any(), any(), any(), anyString()))
                .thenReturn(prenotazioneFinta());

        ResponseEntity<?> resp = controller.bloccaAula(richiestaValida(), admin);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    // ==================== annullaPrenotazione (DELETE) ====================

    @Test
    void annullaReturns404WhenBookingDoesNotExist() {
        when(service.getPrenotazioneById(7L)).thenReturn(null);

        ResponseEntity<?> resp = controller.annullaPrenotazione(7L, utente);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(errorCode(resp)).isEqualTo("PRENOTAZIONE_NOT_FOUND");
    }

    @Test
    void annullaReturns403WhenCallerIsNotTheOwner() {
        Prenotazione altrui = prenotazioneFinta();
        altrui.getUtente().setId(42L); // proprietario diverso dal chiamante (id 1)
        when(service.getPrenotazioneById(7L)).thenReturn(altrui);
        when(service.annullaPrenotazione(7L, 1L, false)).thenReturn(false);

        ResponseEntity<?> resp = controller.annullaPrenotazione(7L, utente);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(errorCode(resp)).isEqualTo("ACCESS_DENIED");
    }

    @Test
    void annullaReturns409WhenBookingIsAlreadyCancelled() {
        // Ramo INVALID_STATE: era irraggiungibile finche' il service non controllava lo stato.
        Prenotazione gia = prenotazioneFinta();
        gia.setStato(StatoPrenotazione.ANNULLATA);
        when(service.getPrenotazioneById(7L)).thenReturn(gia);
        when(service.annullaPrenotazione(7L, 1L, false)).thenReturn(false);

        ResponseEntity<?> resp = controller.annullaPrenotazione(7L, utente);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(errorCode(resp)).isEqualTo("INVALID_STATE");
    }

    @Test
    void adminGets409NotForbiddenOnSomeoneElsesCancelledBooking() {
        // Senza l'allineamento del controllo di proprieta' alla regola del service
        // (proprietario OPPURE admin), qui sarebbe uscito un fuorviante 403.
        Prenotazione altrui = prenotazioneFinta();
        altrui.getUtente().setId(42L);
        altrui.setStato(StatoPrenotazione.ANNULLATA);
        when(service.getPrenotazioneById(7L)).thenReturn(altrui);
        when(service.annullaPrenotazione(7L, 2L, false)).thenReturn(false);

        ResponseEntity<?> resp = controller.annullaPrenotazione(7L, admin);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(errorCode(resp)).isEqualTo("INVALID_STATE");
    }

    @Test
    void annullaReturns200ForOwner() {
        when(service.getPrenotazioneById(7L)).thenReturn(prenotazioneFinta());
        when(service.annullaPrenotazione(7L, 1L, false)).thenReturn(true);

        ResponseEntity<?> resp = controller.annullaPrenotazione(7L, utente);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    /** L'istantanea del proprietario, ora costruita a mano: la tabella utenti non e' piu' qui. */
    private static ProprietarioPrenotazione istantaneaDi(Long id, String username, String nome) {
        return new ProprietarioPrenotazione(id, username, nome);
    }
}
