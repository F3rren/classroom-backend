package com.classroom.exception;

import com.classroom.dto.ApiEnvelope;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.method.MethodValidationResult;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The conflict and internal-error handlers are unreachable from the HTTP tests: on H2 the
 * anti-overlap constraint does not exist, so no request can produce a
 * DataIntegrityViolationException or a BookingConflictException. Here the handler is
 * instantiated directly.
 *
 * The framework's own exceptions go through dispatch() rather than through a handler method
 * called by name. That is deliberate: handleException is the base class's single entry
 * point, it is what decides which status each exception deserves, and calling anything else
 * would test a path no request ever takes.
 */
class GlobalExceptionHandlerUnitTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    /**
     * The route every exception Spring MVC raises actually takes: the base class's entry
     * point picks the status, this class's override supplies the envelope.
     */
    private ResponseEntity<Object> dispatch(Exception ex) throws Exception {
        return Objects.requireNonNull(
                handler.handleException(ex, new ServletWebRequest(new MockHttpServletRequest())));
    }

    private ApiEnvelope<?> envelopeOf(ResponseEntity<Object> response) {
        Object body = Objects.requireNonNull(response.getBody());
        assertThat(body).isInstanceOf(ApiEnvelope.class);
        return (ApiEnvelope<?>) body;
    }

    /** A dummy method, used only to build a valid MethodParameter. */
    @SuppressWarnings("unused")
    private void dummyMethod(String argument) {
    }

    private MethodParameter dummyParameter() throws Exception {
        Method method = getClass().getDeclaredMethod("dummyMethod", String.class);
        return new MethodParameter(Objects.requireNonNull(method), 0);
    }

    /** An empty body, enough to build the exception without dragging in a mock framework. */
    private static HttpInputMessage emptyBody() {
        return new HttpInputMessage() {
            @Override
            public InputStream getBody() {
                return InputStream.nullInputStream();
            }

            @Override
            public HttpHeaders getHeaders() {
                return new HttpHeaders();
            }
        };
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

    // ==================== the framework's exceptions ====================
    //
    // Every one of these answered 500 INTERNAL_ERROR before GlobalExceptionHandler extended
    // ResponseEntityExceptionHandler, because @ExceptionHandler(Exception.class) was
    // consulted before Spring's own resolver and swallowed them all.

    @Test
    void aWrongMethodBecomes405AndSaysWhichOnesAreAllowed() throws Exception {
        ResponseEntity<Object> response = dispatch(
                new HttpRequestMethodNotSupportedException("DELETE", List.of("GET", "POST")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(envelopeOf(response).getError()).isEqualTo("METHOD_NOT_ALLOWED");
        // RFC 9110 requires Allow on a 405, and it is the only way a client learns which
        // methods exist. The base class sets it; this pins that it survives the rewrite of
        // the body.
        assertThat(response.getHeaders().getAllow())
                .containsExactlyInAnyOrder(HttpMethod.GET, HttpMethod.POST);
    }

    @Test
    void anUnsupportedContentTypeBecomes415AndSaysWhatItAccepts() throws Exception {
        ResponseEntity<Object> response = dispatch(new HttpMediaTypeNotSupportedException(
                MediaType.TEXT_PLAIN, List.of(MediaType.APPLICATION_JSON)));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(envelopeOf(response).getError()).isEqualTo("UNSUPPORTED_MEDIA_TYPE");
        assertThat(response.getHeaders().getAccept()).containsExactly(MediaType.APPLICATION_JSON);
    }

    @Test
    void anUnacceptableAcceptBecomes406AndIsTheOneAnswerWithNoBody() throws Exception {
        // The worst case of the family before the change: the envelope built for it was
        // itself unwritable towards a client not accepting JSON, the second failure escaped
        // to the container's error dispatch, and the caller received a 401 asking it to log
        // in. Measured over real HTTP, not supposed - see ProtocolErrorsTest in
        // booking-service, which is what catches a regression here.
        ResponseEntity<Object> response = dispatch(
                new HttpMediaTypeNotAcceptableException(List.of(MediaType.APPLICATION_JSON)));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_ACCEPTABLE);
        // Deliberately bodyless, and the single exception to the one-envelope rule: a body
        // would have to be written in the very representation the client just refused.
        assertThat(response.getBody()).isNull();
    }

    @Test
    void anUnreadableBodyBecomes400() throws Exception {
        ResponseEntity<Object> response = dispatch(
                new HttpMessageNotReadableException("JSON parse error", emptyBody()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(envelopeOf(response).getError()).isEqualTo("BAD_REQUEST");
    }

    @Test
    void aPathVariableOfTheWrongTypeBecomes400() throws Exception {
        // GET /api/rooms/abc: the id cannot be converted to Long. It is the caller's typo,
        // not a defect of ours.
        ResponseEntity<Object> response = dispatch(new MethodArgumentTypeMismatchException(
                "abc", Long.class, "id", dummyParameter(), new NumberFormatException()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(envelopeOf(response).getError()).isEqualTo("BAD_REQUEST");
    }

    @Test
    void aMissingQueryParameterBecomes400() throws Exception {
        ResponseEntity<Object> response = dispatch(
                new MissingServletRequestParameterException("roomId", "Long"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(envelopeOf(response).getError()).isEqualTo("BAD_REQUEST");
    }

    @Test
    void aBodyOverTheSizeLimitBecomes413() throws Exception {
        ResponseEntity<Object> response = dispatch(new MaxUploadSizeExceededException(1024L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(envelopeOf(response).getError()).isEqualTo("PAYLOAD_TOO_LARGE");
    }

    @Test
    void theFrameworksAnswersKeepTheSameEnvelopeAsEveryOtherResponse() throws Exception {
        // The point of the whole change: the status becomes right WITHOUT the response
        // shape changing. A client reading success/userMessage must not have to special-case
        // these.
        ApiEnvelope<?> body = envelopeOf(dispatch(
                new HttpRequestMethodNotSupportedException("PATCH", List.of("GET"))));

        assertThat(body.isSuccess()).isFalse();
        assertThat(body.getUserMessage()).isNotBlank();
        assertThat(body.getTimestamp()).isNotBlank();
        assertThat(body.getSessionId()).isNotBlank();
    }

    // ==================== the two 404s of the framework ====================

    @Test
    void aMissingResourceStays404AndDoesNotBecomeAnInternalError() throws Exception {
        // A regression fixed once before with a handler of its own: without it,
        // NoResourceFoundException fell through to handleGeneric and an unknown path
        // answered 500 INTERNAL_ERROR. The handler is gone - the base class covers this
        // type, and a second mapping for it would stop the context from starting - so what
        // has to be pinned now is that the answer did not change with it.
        ResponseEntity<Object> response = dispatch(
                new NoResourceFoundException(Objects.requireNonNull(HttpMethod.GET), "/v3/api-docs"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(envelopeOf(response).getError()).isEqualTo("NOT_FOUND");
        assertThat(envelopeOf(response).getUserMessage()).isEqualTo("L'indirizzo richiesto non esiste.");
    }

    @Test
    void anUnknownPathWithNoStaticResourcesStays404() throws Exception {
        // Spring picks between two different exceptions depending on whether a
        // static-resource handler exists. The one covered here is the case WITHOUT -
        // booking-service, since the SPA was removed - and it was missing: that service
        // answered 500 "internal error" to any wrong address.
        ResponseEntity<Object> response = dispatch(
                new NoHandlerFoundException("GET", "/percorso/inventato", new HttpHeaders()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(envelopeOf(response).getError()).isEqualTo("NOT_FOUND");
    }

    // ==================== validation ====================

    @Test
    void validationWithNoFieldErrorsFallsBackToAGenericMessage() throws Exception {
        // the firstError == null branch: a BindingResult with no field errors
        BindingResult binding = new BeanPropertyBindingResult(new Object(), "object");

        ResponseEntity<Object> response = dispatch(
                new MethodArgumentNotValidException(dummyParameter(), binding));
        ApiEnvelope<?> body = envelopeOf(response);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(body.getError()).isEqualTo("VALIDATION_ERROR");
        assertThat(body.getUserMessage()).isEqualTo("I dati inviati non sono validi.");
    }

    @Test
    void aParameterConstraintUsesTheMessageWrittenOnTheAnnotation() throws Exception {
        // @Positive(message = "...") on a path variable. The sentence has to survive all the
        // way to userMessage, otherwise moving the rule from an if to an annotation would
        // have traded eight duplicated checks for one useless generic message.
        MessageSourceResolvable violation = new DefaultMessageSourceResolvable(
                new String[]{"Positive"}, null, "L'ID dell'aula deve essere un numero positivo.");
        ParameterValidationResult result = new ParameterValidationResult(
                dummyParameter(), -1L, List.of(violation));

        ApiEnvelope<?> body = envelopeOf(dispatch(new HandlerMethodValidationException(
                MethodValidationResult.create(this, dummyParameter().getMethod(), List.of(result)))));

        // The same code as a rejected body: from the caller's side the two are one thing.
        assertThat(body.getError()).isEqualTo("VALIDATION_ERROR");
        assertThat(body.getUserMessage()).isEqualTo("L'ID dell'aula deve essere un numero positivo.");
    }

    @Test
    void aParameterConstraintWithNoMessageFallsBackToTheGenericOne() throws Exception {
        // ParameterValidationResult refuses an empty error list, so the fallback is reached
        // by a violation that carries no default message - a constraint declared without one.
        MessageSourceResolvable unnamed = new DefaultMessageSourceResolvable(
                new String[]{"Positive"}, null, null);
        ParameterValidationResult result = new ParameterValidationResult(
                dummyParameter(), -1L, List.of(unnamed));

        ApiEnvelope<?> body = envelopeOf(dispatch(new HandlerMethodValidationException(
                MethodValidationResult.create(this, dummyParameter().getMethod(), List.of(result)))));

        assertThat(body.getError()).isEqualTo("VALIDATION_ERROR");
        assertThat(body.getUserMessage()).isEqualTo("I dati inviati non sono validi.");
    }

    @Test
    void validationUsesTheFirstFieldErrorMessage() throws Exception {
        // the target has to have a real field: rejectValue on a field that does not exist
        // would produce a global error and getFieldError() would return null
        BeanPropertyBindingResult binding =
                new BeanPropertyBindingResult(new ObjectWithCapacity(), "objectWithCapacity");
        binding.rejectValue("capacity", "Positive", "La capienza deve essere un numero positivo.");

        ApiEnvelope<?> body = envelopeOf(dispatch(
                new MethodArgumentNotValidException(dummyParameter(), binding)));

        // The per-field Italian sentence written on the DTO is the one thing the generic
        // 400 could not have reproduced, which is why this case keeps an override of its own.
        assertThat(body.getError()).isEqualTo("VALIDATION_ERROR");
        assertThat(body.getUserMessage()).isEqualTo("La capienza deve essere un numero positivo.");
    }

    // ==================== the domain's exceptions ====================

    @Test
    void bookingConflictBecomes409WithItsOwnErrorCode() {
        BookingConflictException ex = new BookingConflictException(
                "UPDATE_CONFLICT", "Impossibile modificare", "Riprova con un altro orario.");

        ResponseEntity<ApiEnvelope<Void>> resp = handler.handleBookingConflict(ex);
        ApiEnvelope<Void> body = Objects.requireNonNull(resp.getBody());

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(body.getError()).isEqualTo("UPDATE_CONFLICT");
        assertThat(body.getUserMessage()).isEqualTo("Riprova con un altro orario.");
        assertThat(body.isSuccess()).isFalse();
    }

    @Test
    void anIntegrityViolationBecomesAGeneric409() {
        ResponseEntity<ApiEnvelope<Void>> resp = handler.handleDataIntegrityViolation(
                new DataIntegrityViolationException("vincolo violato"));
        ApiEnvelope<Void> body = Objects.requireNonNull(resp.getBody());

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(body.getError()).isEqualTo("CONFLICT");
    }

    @Test
    void anUnexpectedExceptionBecomes500WithoutExposingDetails() {
        ResponseEntity<ApiEnvelope<Void>> resp = handler.handleGeneric(
                new IllegalStateException("dettaglio interno che non deve uscire"));
        ApiEnvelope<Void> body = Objects.requireNonNull(resp.getBody());

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(body.getError()).isEqualTo("INTERNAL_ERROR");
        // the technical message must not reach the response
        assertThat(body.getUserMessage()).doesNotContain("dettaglio interno");
        assertThat(body.getMessage()).doesNotContain("dettaglio interno");
    }

    @Test
    void accessDeniedKeepsAnExplicitMessage() {
        ResponseEntity<ApiEnvelope<Void>> resp = handler.handleAccessDenied(
                new AccessDeniedException("Puoi vedere solo le tue prenotazioni."));
        ApiEnvelope<Void> body = Objects.requireNonNull(resp.getBody());

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(body.getUserMessage()).isEqualTo("Puoi vedere solo le tue prenotazioni.");
    }

    @Test
    void aMissingResourceBecomes404WithItsOwnCode() {
        ResponseEntity<ApiEnvelope<Void>> resp = handler.handleResourceNotFound(
                new ResourceNotFoundException("ROOM_NOT_FOUND", "Room not found with id: 42",
                        "L'aula richiesta non esiste."));
        ApiEnvelope<Void> body = Objects.requireNonNull(resp.getBody());

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(body.getError()).isEqualTo("ROOM_NOT_FOUND");
        assertThat(body.getUserMessage()).isEqualTo("L'aula richiesta non esiste.");
        assertThat(body.isSuccess()).isFalse();
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
        ApiEnvelope<Void> body = Objects.requireNonNull(resp.getBody());

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(body.getError()).isEqualTo("ROOM_NAME_TAKEN");
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
        ApiEnvelope<Void> body = Objects.requireNonNull(resp.getBody());

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(body.getError()).isEqualTo("INVALID_STATE");
    }

    @Test
    void wrongCredentialsBecome401WithNoMisleadingChallenge() {
        ResponseEntity<ApiEnvelope<Void>> resp = handler.handleAuthenticationFailed(
                new AuthenticationFailedException("INVALID_CREDENTIALS", "Invalid credentials",
                        "Email o password non corretti."));
        ApiEnvelope<Void> body = Objects.requireNonNull(resp.getBody());

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(body.getError()).isEqualTo("INVALID_CREDENTIALS");
        // No WWW-Authenticate, although RFC 9110 asks a 401 to carry one. The login endpoint
        // does not use HTTP authentication - it reads a JSON body - so a "Bearer" challenge
        // would tell the caller to do the one thing that cannot help. Deliberate, and not the
        // same case as ApiAuthenticationEntryPoint, which does send one.
        assertThat(resp.getHeaders().get(HttpHeaders.WWW_AUTHENTICATE)).isNull();
    }

    @Test
    void aRateLimitBecomes429AndSaysHowLongToWait() {
        ResponseEntity<ApiEnvelope<Void>> resp = handler.handleTooManyRequests(
                new TooManyRequestsException("TOO_MANY_ATTEMPTS", "Too many login attempts",
                        "Riprova tra qualche minuto.", 42L));
        ApiEnvelope<Void> body = Objects.requireNonNull(resp.getBody());

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(body.getError()).isEqualTo("TOO_MANY_ATTEMPTS");
        // The delay is known only to whoever threw, so it travels on the exception. Without
        // it a client guesses, and usually guesses too soon. RFC 9110 section 10.2.3.
        assertThat(resp.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("42");
    }

    @Test
    void aDownstreamFailureBecomes503() {
        ResponseEntity<ApiEnvelope<Void>> resp = handler.handleServiceUnavailable(
                new ServiceUnavailableException("SERVICE_UNAVAILABLE", "notification-service did not answer",
                        "Il servizio non e' momentaneamente disponibile."));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(Objects.requireNonNull(resp.getBody()).getError()).isEqualTo("SERVICE_UNAVAILABLE");
    }

    @Test
    void anIllegalArgumentStaysA500AndDoesNotBecomeTheCallersFault() {
        // Deliberate: IllegalArgumentException signals a programming error. Mapping it to a
        // 400 would pass the server's bugs off as bad requests, and would hide exactly the
        // cases that need to be seen. The base class does not claim this type either, so it
        // still reaches handleGeneric.
        ResponseEntity<ApiEnvelope<Void>> resp = handler.handleGeneric(
                new IllegalArgumentException("argomento non valido"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
