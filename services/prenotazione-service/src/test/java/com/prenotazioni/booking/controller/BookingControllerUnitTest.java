package com.prenotazioni.booking.controller;

import com.prenotazioni.dto.ApiEnvelope;
import com.prenotazioni.booking.dto.BookingRequest;
import com.prenotazioni.exception.BookingConflictException;
import com.prenotazioni.exception.DomainConflictException;
import com.prenotazioni.booking.model.Room;
import com.prenotazioni.booking.model.Booking;
import com.prenotazioni.booking.model.BookingStatus;
import com.prenotazioni.booking.model.BookingOwner;
import com.prenotazioni.security.AppPrincipal;
import com.prenotazioni.booking.service.BookingService;
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
// I tre test annullaReturns404/403/409 sono stati rimossi con i rami che verificavano:
// il controller non ricostruisce piu' il motivo di un fallimento per scegliere lo status.
// Quei casi sono ora coperti da PrenotazioneServiceUnitTest, che verifica quale eccezione
// venga lanciata, e da GlobalExceptionHandlerUnitTest, che verifica in quale status si
// traduca. Prima erano un solo test perche' erano un solo blocco di codice.
class BookingControllerUnitTest {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private BookingService service;
    private BookingController controller;
    private AppPrincipal user;
    private AppPrincipal admin;

    @BeforeEach
    void setUp() {
        service = mock(BookingService.class);
        controller = new BookingController(service);
        user = new AppPrincipal(1L, "user@test.it", "m.rossi", "Mario Rossi", "user");
        admin = new AppPrincipal(2L, "admin@test.it", "m.rossi", "Mario Rossi", "admin");
    }

    // ---------- helper ----------

    private BookingRequest request(String inizio, String fine) {
        BookingRequest r = new BookingRequest();
        r.setAulaId(10L);
        r.setCorsoId(null);
        r.setInizio(inizio);
        r.setFine(fine);
        r.setDescrizione("descrizione di test");
        return r;
    }

    private BookingRequest richiestaValida() {
        LocalDateTime inizio = LocalDateTime.now().plusDays(1).withNano(0);
        return request(inizio.format(ISO), inizio.plusHours(2).format(ISO));
    }

    private Booking prenotazioneFinta() {
        Room room = new Room();
        room.setId(10L);
        room.setNome("Aula Finta");
        BookingOwner u = new BookingOwner(1L, "utente", "Utente Test");
        Booking p = new Booking();
        p.setId(99L);
        p.setAula(room);
        p.setUtente(istantaneaDi(u.getId(), u.getUsername(), u.getNome()));
        p.setInizio(LocalDateTime.now().plusDays(1));
        p.setFine(LocalDateTime.now().plusDays(1).plusHours(2));
        p.setStato(BookingStatus.PRENOTATA);
        return p;
    }

    @SuppressWarnings("unchecked")
    private String errorCode(ResponseEntity<?> resp) {
        return ((ApiEnvelope<Object>) resp.getBody()).getError();
    }

    // ==================== prenotaAula ====================

    @Test
    void prenotaAulaRejectsUnparsableStartDate() {
        ResponseEntity<?> resp = controller.bookRoom(request("non-una-data", "2030-01-01T12:00:00"), user);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(errorCode(resp)).isEqualTo("INVALID_START_DATE");
    }

    @Test
    void prenotaAulaRejectsUnparsableEndDate() {
        ResponseEntity<?> resp = controller.bookRoom(request("2030-01-01T10:00:00", "non-una-data"), user);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(errorCode(resp)).isEqualTo("INVALID_END_DATE");
    }

    @Test
    void prenotaAulaRejectsEndBeforeStart() {
        ResponseEntity<?> resp = controller.bookRoom(
                request("2030-01-01T12:00:00", "2030-01-01T10:00:00"), user);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(errorCode(resp)).isEqualTo("INVALID_DATE_RANGE");
    }

    @Test
    void prenotaAulaRejectsDateInThePast() {
        LocalDateTime passato = LocalDateTime.now().minusDays(2).withNano(0);
        ResponseEntity<?> resp = controller.bookRoom(
                request(passato.format(ISO), passato.plusHours(1).format(ISO)), user);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(errorCode(resp)).isEqualTo("PAST_DATE");
    }

    @Test
    void prenotaAulaLasciaSalireIlConflitto() {
        // Il controller non traduce piu': il tipo dell'eccezione porta gia' la causa e
        // GlobalExceptionHandler decide lo status una volta sola. Qui si verifica che
        // non la intercetti, che e' il comportamento corretto dopo la conversione.
        when(service.bookRoom(anyLong(), any(), any(), any(), any(), anyString())).thenThrow(new BookingConflictException("BOOKING_CONFLICT", "occupata", "L'aula non e' disponibile."));

        assertThatThrownBy(() -> controller.bookRoom(richiestaValida(), user))
                .isInstanceOf(BookingConflictException.class);
    }

    @Test
    void prenotaAulaTranslatesDbConstraintIntoBookingConflict() {
        when(service.bookRoom(anyLong(), any(), any(), any(), any(), anyString()))
                .thenThrow(new DataIntegrityViolationException("prenotazioni_no_overlap"));

        BookingRequest req = richiestaValida();
        assertThatThrownBy(() -> controller.bookRoom(req, user))
                .isInstanceOf(BookingConflictException.class)
                .satisfies(e -> assertThat(((BookingConflictException) e).getErrorCode()).isEqualTo("BOOKING_CONFLICT"));
    }

    @Test
    void prenotaAulaReturns201OnSuccess() {
        when(service.bookRoom(anyLong(), any(), any(), any(), any(), anyString()))
                .thenReturn(prenotazioneFinta());

        ResponseEntity<?> resp = controller.bookRoom(richiestaValida(), user);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    // ==================== modificaPrenotazione (PUT) ====================

    @Test
    void modificaRejectsUnparsableStartDate() {
        ResponseEntity<?> resp = controller.editBooking(
                5L, request("boom", "2030-01-01T12:00:00"), user);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(errorCode(resp)).isEqualTo("INVALID_START_DATE");
    }

    @Test
    void modificaRejectsUnparsableEndDate() {
        ResponseEntity<?> resp = controller.editBooking(
                5L, request("2030-01-01T10:00:00", "boom"), user);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(errorCode(resp)).isEqualTo("INVALID_END_DATE");
    }

    @Test
    void modificaRejectsEndBeforeStart() {
        ResponseEntity<?> resp = controller.editBooking(
                5L, request("2030-01-01T12:00:00", "2030-01-01T10:00:00"), user);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(errorCode(resp)).isEqualTo("INVALID_DATE_RANGE");
    }

    @Test
    void modificaRejectsDateInThePast() {
        LocalDateTime passato = LocalDateTime.now().minusDays(2).withNano(0);
        ResponseEntity<?> resp = controller.editBooking(
                5L, request(passato.format(ISO), passato.plusHours(1).format(ISO)), user);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(errorCode(resp)).isEqualTo("PAST_DATE");
    }

    @Test
    void modificaLasciaSalireIlConflitto() {
        // Il controller non traduce piu': il tipo dell'eccezione porta gia' la causa e
        // GlobalExceptionHandler decide lo status una volta sola. Qui si verifica che
        // non la intercetti, che e' il comportamento corretto dopo la conversione.
        when(service.updateBooking(anyLong(), anyLong(), any(), anyLong(), anyBoolean(), any(), any(), anyString())).thenThrow(new BookingConflictException("UPDATE_CONFLICT", "occupata", "L'aula non e' disponibile."));

        assertThatThrownBy(() -> controller.editBooking(5L, richiestaValida(), user))
                .isInstanceOf(BookingConflictException.class);
    }

    @Test
    void modificaTranslatesDbConstraintIntoUpdateConflict() {
        when(service.updateBooking(anyLong(), anyLong(), any(), anyLong(), anyBoolean(), any(), any(), anyString()))
                .thenThrow(new DataIntegrityViolationException("prenotazioni_no_overlap"));

        BookingRequest req = richiestaValida();
        assertThatThrownBy(() -> controller.editBooking(5L, req, user))
                .isInstanceOf(BookingConflictException.class)
                .satisfies(e -> assertThat(((BookingConflictException) e).getErrorCode()).isEqualTo("UPDATE_CONFLICT"));
    }

    @Test
    void modificaReturns200OnSuccess() {
        when(service.updateBooking(anyLong(), anyLong(), any(), anyLong(), anyBoolean(), any(), any(), anyString()))
                .thenReturn(prenotazioneFinta());

        ResponseEntity<?> resp = controller.editBooking(5L, richiestaValida(), user);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ==================== bloccaAula ====================

    @Test
    void bloccaRejectsUnparsableStartDate() {
        ResponseEntity<?> resp = controller.blockRoom(request("boom", "2030-01-01T12:00:00"), admin);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(errorCode(resp)).isEqualTo("INVALID_START_DATE");
    }

    @Test
    void bloccaRejectsUnparsableEndDate() {
        ResponseEntity<?> resp = controller.blockRoom(request("2030-01-01T10:00:00", "boom"), admin);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(errorCode(resp)).isEqualTo("INVALID_END_DATE");
    }

    @Test
    void bloccaRejectsEndBeforeStart() {
        ResponseEntity<?> resp = controller.blockRoom(
                request("2030-01-01T12:00:00", "2030-01-01T10:00:00"), admin);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(errorCode(resp)).isEqualTo("INVALID_DATE_RANGE");
    }

    @Test
    void bloccaLasciaSalireIlConflitto() {
        // Il controller non traduce piu': il tipo dell'eccezione porta gia' la causa e
        // GlobalExceptionHandler decide lo status una volta sola. Qui si verifica che
        // non la intercetti, che e' il comportamento corretto dopo la conversione.
        when(service.blockRoom(anyLong(), any(), any(), any(), anyString())).thenThrow(new BookingConflictException("BLOCK_CONFLICT", "occupata", "L'aula non e' disponibile."));

        assertThatThrownBy(() -> controller.blockRoom(richiestaValida(), admin))
                .isInstanceOf(BookingConflictException.class);
    }

    @Test
    void bloccaTranslatesDbConstraintIntoBlockConflict() {
        when(service.blockRoom(anyLong(), any(), any(), any(), anyString()))
                .thenThrow(new DataIntegrityViolationException("prenotazioni_no_overlap"));

        BookingRequest req = richiestaValida();
        assertThatThrownBy(() -> controller.blockRoom(req, admin))
                .isInstanceOf(BookingConflictException.class)
                .satisfies(e -> assertThat(((BookingConflictException) e).getErrorCode()).isEqualTo("BLOCK_CONFLICT"));
    }

    @Test
    void bloccaReturns201OnSuccess() {
        when(service.blockRoom(anyLong(), any(), any(), any(), anyString()))
                .thenReturn(prenotazioneFinta());

        ResponseEntity<?> resp = controller.blockRoom(richiestaValida(), admin);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    // ==================== annullaPrenotazione (DELETE) ====================

    @Test
    void unAdminSuUnaPrenotazioneAltruiGiaAnnullataOttieneIlConflittoNonUnDivieto() {
        // Il test nasceva per proteggere da un 403 fuorviante: il controller riderivava
        // la regola di proprieta' e, se sbagliava l'ordine dei controlli, un admin che
        // annullava una prenotazione altrui gia' annullata si vedeva dire "puoi annullare
        // solo le tue". Ora quella duplicazione non esiste: e' il service a decidere, e
        // lancia il conflitto sullo stato. Qui resta a fissare che il controller non
        // reintroduca una propria interpretazione.
        when(service.cancelBooking(7L, 2L, true))
                .thenThrow(new DomainConflictException("INVALID_STATE", "gia' annullata",
                        "Questa prenotazione non puo' essere annullata nello stato attuale."));

        assertThatThrownBy(() -> controller.cancelBooking(7L, admin))
                .isInstanceOf(DomainConflictException.class);
    }

    @Test
    void annullaReturns200ForOwner() {
        when(service.getBookingById(7L)).thenReturn(prenotazioneFinta());
        when(service.cancelBooking(7L, 1L, false)).thenReturn(true);

        ResponseEntity<?> resp = controller.cancelBooking(7L, user);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    /** L'istantanea del proprietario, ora costruita a mano: la tabella utenti non e' piu' qui. */
    private static BookingOwner istantaneaDi(Long id, String username, String nome) {
        return new BookingOwner(id, username, nome);
    }
}
