package com.classroom.exception;

import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.NoHandlerFoundException;
import com.classroom.dto.ApiEnvelope;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The conflict and internal-error handlers are unreachable from the HTTP tests: on H2 the
 * anti-overlap constraint does not exist, so no request can produce a
 * DataIntegrityViolationException or a BookingConflictException. Here the handler is
 * instantiated directly.
 */
class GlobalExceptionHandlerUnitTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    /** A dummy method, used only to build a valid MethodParameter. */
    @SuppressWarnings("unused")
    private void dummyMethod(String argument) {
    }

    /**
     * BeanPropertyBindingResult requires the rejected field to really exist on the target,
     * otherwise rejectValue produces a global error and getFieldError() returns null. It used
     * to use RoomRequest, which lives in the application module: a local bean is enough here.
     */
    static class ObjectWithCapacity {
        private Integer capacity;

        public Integer getCapacity() {
            return capacity;
        }

        public void setCapacity(Integer capacity) {
            this.capacity = capacity;
        }
    }

    @Test
    void bookingConflictBecomes409WithItsOwnErrorCode() {
        BookingConflictException ex = new BookingConflictException(
                "UPDATE_CONFLICT", "Impossibile modificare", "Riprova con un altro orario.");

        ResponseEntity<ApiEnvelope<Void>> resp = handler.handleBookingConflict(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resp.getBody().getError()).isEqualTo("UPDATE_CONFLICT");
        assertThat(resp.getBody().getUserMessage()).isEqualTo("Riprova con un altro orario.");
        assertThat(resp.getBody().isSuccess()).isFalse();
    }

    @Test
    void anIntegrityViolationBecomesAGeneric409() {
        ResponseEntity<ApiEnvelope<Void>> resp = handler.handleDataIntegrityViolation(
                new DataIntegrityViolationException("vincolo violato"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resp.getBody().getError()).isEqualTo("CONFLICT");
    }

    @Test
    void aMissingResourceStays404AndDoesNotBecomeAnInternalError() {
        // A regression: without a dedicated handler, NoResourceFoundException fell
        // through to handleGeneric, and an unknown path answered 500 INTERNAL_ERROR.
        ResponseEntity<ApiEnvelope<Void>> resp = handler.handleResourceNotFound(
                new NoResourceFoundException(HttpMethod.GET, "/v3/api-docs"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody().getError()).isEqualTo("NOT_FOUND");
    }

    @Test
    void anUnexpectedExceptionBecomes500WithoutExposingDetails() {
        ResponseEntity<ApiEnvelope<Void>> resp = handler.handleGeneric(
                new IllegalStateException("dettaglio interno che non deve uscire"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(resp.getBody().getError()).isEqualTo("INTERNAL_ERROR");
        // the technical message must not reach the response
        assertThat(resp.getBody().getUserMessage()).doesNotContain("dettaglio interno");
    }

    @Test
    void accessDeniedKeepsAnExplicitMessage() {
        ResponseEntity<ApiEnvelope<Void>> resp = handler.handleAccessDenied(
                new AccessDeniedException("Puoi vedere solo le tue prenotazioni."));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resp.getBody().getUserMessage()).isEqualTo("Puoi vedere solo le tue prenotazioni.");
    }

    @Test
    void validationWithNoFieldErrorsFallsBackToAGenericMessage() throws Exception {
        // the firstError == null branch: a BindingResult with no field errors
        Method method = getClass().getDeclaredMethod("dummyMethod", String.class);
        MethodParameter parameter = new MethodParameter(method, 0);
        BindingResult binding = new BeanPropertyBindingResult(new Object(), "object");

        ResponseEntity<ApiEnvelope<Void>> resp = handler.handleValidation(
                new MethodArgumentNotValidException(parameter, binding));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().getError()).isEqualTo("VALIDATION_ERROR");
        assertThat(resp.getBody().getUserMessage()).isEqualTo("I dati inviati non sono validi.");
    }

    @Test
    void validationUsesTheFirstFieldErrorMessage() throws Exception {
        Method method = getClass().getDeclaredMethod("dummyMethod", String.class);
        MethodParameter parameter = new MethodParameter(method, 0);
        // the target has to have a real field: rejectValue on a field that does not exist
        // would produce a global error and getFieldError() would return null
        BeanPropertyBindingResult binding =
                new BeanPropertyBindingResult(new ObjectWithCapacity(), "objectWithCapacity");
        binding.rejectValue("capacity", "Positive", "La capienza deve essere un numero positivo.");

        ResponseEntity<ApiEnvelope<Void>> resp = handler.handleValidation(
                new MethodArgumentNotValidException(parameter, binding));

        assertThat(resp.getBody().getUserMessage()).isEqualTo("La capienza deve essere un numero positivo.");
    }

    @Test
    void aMissingResourceBecomes404WithItsOwnCode() {
        ResponseEntity<ApiEnvelope<Void>> resp = handler.handleResourceNotFound(
                new ResourceNotFoundException("ROOM_NOT_FOUND", "Room not found with id: 42",
                        "L'aula richiesta non esiste."));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody().getError()).isEqualTo("ROOM_NOT_FOUND");
        assertThat(resp.getBody().getUserMessage()).isEqualTo("L'aula richiesta non esiste.");
        assertThat(resp.getBody().isSuccess()).isFalse();
    }

    @Test
    void theByIdShortcutComposesBothTheTechnicalAndTheUserMessage() {
        // No ResourceType here: that enum now lives one per service (booking-service's,
        // auth-service's), and shared cannot depend on either. This is the generic
        // mechanism every service's own enum composes through - see booking-service's or
        // auth-service's ResourceType for the version a call site actually uses.
        ResourceNotFoundException ex = ResourceNotFoundException.forId(
                "Room", "ROOM_NOT_FOUND", "L'aula richiesta non esiste.", 42L);

        // the technical one carries the id, useful in a log; the user's does not, because
        // they have no use for it
        assertThat(ex.getMessage()).contains("42");
        assertThat(ex.getUserMessage()).doesNotContain("42");
        assertThat(ex.getErrorCode()).isEqualTo("ROOM_NOT_FOUND");
    }

    @Test
    void aDomainConflictBecomes409() {
        // 409 and not 400: a name already taken is not a malformed request, and the caller
        // does not fix it by correcting the syntax.
        ResponseEntity<ApiEnvelope<Void>> resp = handler.handleDomainConflict(
                new DomainConflictException("ROOM_NAME_TAKEN", "Room name already taken: Aula Magna",
                        "Esiste gia' un'aula con questo nome."));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resp.getBody().getError()).isEqualTo("ROOM_NAME_TAKEN");
    }

    @Test
    void aBookingConflictStaysHandleableAsAGenericConflict() {
        // BookingConflictException is a subtype: the specific handler takes precedence, but
        // if it were ever removed the case would still be covered, with the same status.
        // This test pins that relationship down.
        BookingConflictException ex = new BookingConflictException(
                "BOOKING_CONFLICT", "Sovrapposizione", "L'aula e' gia' prenotata.");

        assertThat(ex).isInstanceOf(DomainConflictException.class);
        assertThat(handler.handleDomainConflict(ex).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void anInvalidRequestBecomes400() {
        ResponseEntity<ApiEnvelope<Void>> resp = handler.handleInvalidRequest(
                new InvalidRequestException("INVALID_STATE", "Invalid status: inventato",
                        "Stato non riconosciuto."));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().getError()).isEqualTo("INVALID_STATE");
    }

    @Test
    void anIllegalArgumentStaysA500AndDoesNotBecomeTheCallersFault() {
        // Deliberate: IllegalArgumentException signals a programming error. Mapping it to a
        // 400 would pass the server's bugs off as bad requests, and would hide exactly the
        // cases that need to be seen.
        ResponseEntity<ApiEnvelope<Void>> resp = handler.handleGeneric(
                new IllegalArgumentException("argomento non valido"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void anUnknownPathWithNoStaticResourcesStays404() {
        // Spring picks between two different exceptions depending on whether a
        // static-resource handler exists. The one covered here is the case WITHOUT -
        // booking-service, since the SPA was removed - and it was missing: that service
        // answered 500 "internal error" to any wrong address.
        var response = handler.handleNoHandler(
                new NoHandlerFoundException("GET", "/percorso/inventato", new HttpHeaders()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getError()).isEqualTo("NOT_FOUND");
    }
}
